/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.eclipse.docgenerator.generators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.IPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.servoy.eclipse.docgenerator.metamodel.ClientSupport;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;
import com.servoy.eclipse.docgenerator.util.Pair;

/**
 * @author gerzse
 */
@SuppressWarnings("nls")
public class DefaultDocumentationGenerator implements IDocumentationGenerator
{
	public static final String STORE_KEY = DefaultDocumentationGenerator.class.getCanonicalName();

	// tags and attributes used in XML
	private static final String TAG_SERVOYEXTENSION = "servoyextension";
	private static final String TAG_SERVOYDOC = "servoydoc";
	// for class level
	private static final String TAG_OBJECT = "object";
	private static final String ATTR_PUBLICNAME = "publicName";
	private static final String ATTR_SCRIPTINGNAME = "scriptingName";
	private static final String ATTR_QUALIFIEDNAME = "qualifiedName";
	private static final String ATTR_EXTENDSCOMPONENT = "extendsComponent";
	public static final String ATTR_CLIENT_SUPPORT = "clientSupport";
	protected static final String ATTR_DEPRECATED = "deprecated";
	public static final String TAG_CONSTANTS = "constants";
	public static final String TAG_PROPERTIES = "properties";
	public static final String TAG_CONSTRUCTORS = "constructors";
	public static final String TAG_FUNCTIONS = "functions";
	public static final String TAG_FUNCTION = "function";
	public static final String TAG_PROPERTY = "property";
	public static final String TAG_CONSTRUCTOR = "constructor";
	protected static final String ATTR_TYPECODE = "typecode";
	protected static final String ATTR_TYPE = "type";
	// for fields
	public static final String TAG_CONSTANT = "constant";

	private final Set<String> solved = new HashSet<String>();

	public String getID()
	{
		return STORE_KEY;
	}

	public InputStream generate(DocumentationGenerationRequest req, MetaModelHolder holder, Set<DocumentationWarning> allWarnings, IPath outputPath)
	{
		TypeMapper typeMapper = new TypeMapper(req.tryToMapUndocumentedTypes());
		IStoragePlaceFactory storageFactory = getDataFactory(typeMapper);
		MemberKindIndex availableMemberKinds = getMemberKindIndex();

		createStoragePlaceForAll(holder, storageFactory);
		solveDependencies(holder);
		recomputeForAll(holder);
		doTypeMappingForAll(holder, typeMapper);

		// Post-processing, just before sending to XML.
		req.postProcess(holder, outputPath);

		collectAllWarnings(holder, allWarnings);

		return writeToXML(holder, req.getCategoryFilter(), req.autopilot(), availableMemberKinds);
	}

	@SuppressWarnings("unused")
	protected IStoragePlaceFactory getDataFactory(TypeMapper typeMapper)
	{
		return new DefaultStoragePlaceFactory();
	}

	protected MemberKindIndex getMemberKindIndex()
	{
		return new MemberKindIndex();
	}

	protected boolean includeSample()
	{
		return true;
	}

	private void createStoragePlaceForAll(MetaModelHolder holder, IStoragePlaceFactory mdFactory)
	{
		for (TypeMetaModel typeMM : holder.getTypes())
		{
			if (!typeMM.getStore().containsKey(STORE_KEY))
			{
				TypeStoragePlace typeData = new TypeStoragePlace(typeMM);
				typeMM.getStore().put(STORE_KEY, typeData);
			}

			for (IMemberMetaModel memberMM : typeMM.getMembers())
			{
				if (!memberMM.getStore().containsKey(STORE_KEY))
				{
					MemberStoragePlace memberData = mdFactory.getData(holder, typeMM, memberMM);
					memberMM.getStore().put(STORE_KEY, memberData);
				}
			}
		}
	}

	/**
	 * Solved dependencies, namely the @sameas, @sampleas and @clonedesc tags used inside Javadocs.
	 * Also copies documentation from interfaces to implementing classes, if needed.
	 */
	private void solveDependencies(MetaModelHolder holder)
	{
		for (TypeMetaModel typeMM : holder.getTypes())
		{
			// try to fix the "extendsComponent", in case it holds just a public name and not a qualified name
			TypeStoragePlace typeData = (TypeStoragePlace)typeMM.getStore().get(STORE_KEY);
			if (typeData.getExtendsComponent() != null)
			{
				if (!holder.hasType(typeData.getExtendsComponent()))
				{
					for (TypeMetaModel candidate : holder.getTypes())
					{
						if (typeData.getExtendsComponent().equals(candidate.getPublicName()))
						{
							typeData.setExtendsComponent(candidate.getName().getQualifiedName());
							break;
						}
					}
				}
			}

			// solve @sameas, @sampleas and @clonedesc references
			for (IMemberMetaModel member : typeMM.getMembers())
			{
				solveDependenciesForMember(member, holder, new Stack<String>());
			}
		}
	}

	/**
	 * Solves dependencies between this member and any other member referenced from it.
	 * The @sameas, @sampleas and @clonedesc tags are considered when visiting references.
	 */
	private void solveDependenciesForMember(IMemberMetaModel memberMM, MetaModelHolder holder, Stack<String> visited)
	{
		// If already solved, do nothing.
		if (!solved.contains(memberMM.getFullSignature()) || memberMM.getFullSignature().equals(memberMM.getIndexSignature()))
		{
			// if it is inherited may be present multiple times
			String fullSig = memberMM.getFullSignature();
			// If we are already visiting this member, then we got a circular dependency.
			if (visited.contains(fullSig))
			{
				StringBuffer sb = new StringBuffer();
				sb.append("Cyclic dependency detected while resolving documentation:");
				for (String s : visited)
					sb.append("\n").append(s);
				sb.append("\n").append(fullSig);
				DocumentationWarning dw = new DocumentationWarning(WarningType.RedirectionProblem, fullSig, sb.toString());
				memberMM.getWarnings().add(dw);
			}
			else
			{
				visited.push(fullSig);
				// If the member has Javadoc, look into it for references.
				MemberStoragePlace memberData = (MemberStoragePlace)memberMM.getStore().get(STORE_KEY);
				DocumentationDataDistilled docData = memberData.getDocData();
				if (docData != null)
				{
					if (docData.getSameAs() != null)
					{
						if (docData.getCloneSample() != null)
						{
							memberMM.getWarnings().add(
								new DocumentationWarning(WarningType.RedirectionProblem, fullSig, "The " + DocumentationDataDistilled.TAG_SAMPLE_AS +
									" tag is ignored because it is used together with " + DocumentationDataDistilled.TAG_SAMEAS + "."));
						}
						if (docData.getCloneDescription() != null)
						{
							memberMM.getWarnings().add(
								new DocumentationWarning(WarningType.RedirectionProblem, fullSig, "The " + DocumentationDataDistilled.TAG_CLONEDESC +
									" tag is ignored because it is used together with " + DocumentationDataDistilled.TAG_SAMEAS + "."));
						}
						IMemberMetaModel target = find(holder, memberMM, docData.getSameAs(), true);
						if (target != null)
						{
							solveDependenciesForMember(target, holder, visited);
							MemberStoragePlace targetData = (MemberStoragePlace)target.getStore().get(STORE_KEY);
							if (targetData.getDocData() == null)
							{
								memberMM.getWarnings().add(
									new DocumentationWarning(WarningType.RedirectionProblem, memberMM.getFullSignature(),
										"Target of redirection is not documented: " + docData.getSameAs().toString()));
							}
							else
							{
								memberData.setDocData(targetData.getDocData());
							}
						}
					}
					else
					{
						if (docData.getCloneSample() != null)
						{
							IMemberMetaModel target = find(holder, memberMM, docData.getCloneSample(), true);
							if (target != null)
							{
								solveDependenciesForMember(target, holder, visited);
								MemberStoragePlace targetData = (MemberStoragePlace)target.getStore().get(STORE_KEY);
								if (targetData.getDocData() == null)
								{
									memberMM.getWarnings().add(
										new DocumentationWarning(WarningType.RedirectionProblem, memberMM.getFullSignature(),
											"Target of redirection is not documented: " + docData.getCloneSample().toString()));
								}
								else
								{
									docData.setSamples(targetData.getDocData().getSamples());
								}
							}
						}
						if (docData.getCloneDescription() != null)
						{
							IMemberMetaModel target = find(holder, memberMM, docData.getCloneDescription(), true);
							if (target != null)
							{
								solveDependenciesForMember(target, holder, visited);
								MemberStoragePlace targetData = (MemberStoragePlace)target.getStore().get(STORE_KEY);
								if (targetData.getDocData() == null)
								{
									memberMM.getWarnings().add(
										new DocumentationWarning(WarningType.RedirectionProblem, memberMM.getFullSignature(),
											"Target of redirection is not documented: " + docData.getCloneDescription().toString()));
								}
								else
								{
									docData.setTexts(targetData.getDocData().getTexts());
								}
							}
						}
					}
				}
				visited.pop();
			}
			solved.add(memberMM.getFullSignature());
		}
	}

	/**
	 * Finds a specific member from a specific class, if it exists. Optionally raises a warning if the searched
	 * method or its parent class are not found.
	 */
	private IMemberMetaModel find(MetaModelHolder holder, IMemberMetaModel target, QualifiedNameRaw redirect, boolean reportMissing)
	{
		TypeMetaModel typeMM = holder.getType(redirect.getClassName());
		if (typeMM != null)
		{
			for (IMemberMetaModel candidate : typeMM.getMembers(holder))
			{
				if (candidate.matchesSignature(redirect.getMemberSignature()))
				{
					return candidate;
				}
			}
			if (reportMissing)
			{
				target.getWarnings().add(
					new DocumentationWarning(WarningType.RedirectionProblem, target.getFullSignature(), "Cannot find target member of redirection: " +
						redirect.toString()));
			}
		}
		else if (reportMissing)
		{
			target.getWarnings().add(
				new DocumentationWarning(WarningType.RedirectionProblem, target.getFullSignature(), "Cannot find target class of redirection: " +
					redirect.toString()));
		}
		return null;
	}


	public void doTypeMappingForAll(MetaModelHolder holder, TypeMapper typeMapper)
	{
		for (TypeMetaModel typeMM : holder.getTypes())
		{
			for (IMemberMetaModel memberMM : typeMM.getMembers())
			{
				MemberStoragePlace memberData = (MemberStoragePlace)memberMM.getStore().get(DefaultDocumentationGenerator.STORE_KEY);
				DocumentationDataDistilled doc = memberData.getDocData();
				if (doc != null)
				{
					substituteLinksWithPublicName(holder, typeMM, doc);

					if (doc.getParameters() != null)
					{
						for (DocumentedParameterData par : doc.getParameters())
						{
							par.checkIfHasType(holder, typeMapper);
						}
					}
				}
				memberData.mapTypes(typeMapper);
			}
		}
	}

	/**
	 * @param holder
	 * @param typeMM
	 * @param doc
	 */
	private void substituteLinksWithPublicName(MetaModelHolder holder, TypeMetaModel typeMM, DocumentationDataDistilled doc)
	{
		// replace @link
		for (int i = 0; i < doc.getLinks().size(); i++)
		{
			String newLinkContent = resolvePublicLinks(holder, typeMM, doc.getLinks().get(i));
			doc.getLinks().set(i, newLinkContent);
		}
		//replace Deprecated 
		if (doc.getDeprecatedText() != null)
		{
			String newTextContent = resolvePublicLinks(holder, typeMM, doc.getDeprecatedText());
			doc.setDeprecatedText(newTextContent);
		}
		//
		if (doc.getTexts() != null)
		{
			for (Pair<ClientSupport, String> docText : doc.getTexts())
			{
				if (docText.getRight() != null)
				{
					String newTextContent = resolvePublicLinks(holder, typeMM, docText.getRight());
					docText.setRight(newTextContent);
				}
			}
		}
	}

	/**
	 * Input : 
	 * linkContent , ex :  <p>1 - com.servoy.extensions.plugins.dialog.DialogProvider#js_showDialog() <br/>
	 *                     2 - com.servoy.extensions.plugins.dialog.DialogProvider<br/>
	 *                     3 - #js_showDialog() <br/>
	 *                     4 - http://www.quartz-scheduler.org/docs/tutorials/crontrigger.html <br/>
	 *                     5 - Lorem ipsum  com.servoy.extensions.plugins.dialog.DialogProvider#js_showDialog() , com.servoy.extensions.plugins.dialog.DialogProvider#js_showDialog() <br/>
	 *                     </p>
	 * 
	 * Output (return) , ex: <p>1 - dialogs#showDialog()  <br/>
	 *                       2 - dialogs <br/>
	 *                       3 - #showDialog <br/>
	 *                       4 -  http://www.quartz-scheduler.org/docs/tutorials/crontrigger.html  (the same as input) <br/>
	 *                       5 -  Lorem ipsum dialogs#showDialog() , dialogs#showDialog() <br/>
	 *                       </p>
	 * @param holder
	 * @param currentTypeMM
	 * @param linkContent
	 */
	private String resolvePublicLinks(MetaModelHolder holder, TypeMetaModel currentTypeMM, String linkContent)
	{
		//pattern mathcing xxx.yy.zzz#tttt or xxx.yy.zzz#tttt  or xxx.yy.zzz#tttt(aa, aa[])
		String packagePattern = "(([a-zA-Z\\$][\\w\\d\\$]*\\.)+)([a-zA-Z\\$][\\w\\$])*";
		String functionSignaturePattern = "(#\\w*\\s*(\\([\\w\\s,\\[\\]<>\\.]*\\))?)?"; //function name is optional , also '(params)' is optional
		Pattern pattern = Pattern.compile("(" + packagePattern + functionSignaturePattern + ")");

		Matcher matcher = pattern.matcher(linkContent);
		StringBuffer finalString = new StringBuffer();
		String currentMatch = linkContent;
		while (matcher.find())
		{
			currentMatch = matcher.group(0);
			String[] parts = currentMatch.split("#");
			String qName = parts[0];
			//fully qualified name case
			TypeMetaModel mm = holder.getType(qName);
			if (mm != null)
			{
				//replace oldpackage name with new public name and if method was not present(only full qName was given ) do not append '#'
				currentMatch = currentMatch.replaceAll(packagePattern + functionSignaturePattern,/* "$1" */
					mm.getPublicName() + (parts.length > 1 ? "#" + parts[1] : ""));

			}
			matcher.appendReplacement(finalString, currentMatch);

		}
		matcher.appendTail(finalString);
		if (finalString.length() > 1)
		{
			return finalString.toString().replaceAll("#js_", "#");
		}
		else
		{
			return linkContent.replaceAll("#js_", "#");
		}
	}

	/**
	 * Give a chance to recompute some data, now that dependencies have been solved.
	 */
	@SuppressWarnings("unused")
	protected void recomputeForAll(MetaModelHolder holder)
	{
		// nothing here, just an extension point via subclassing.
	}

	/**
	 * Generates a memory based stream with the content of the XML file. The generated stream
	 * is fed to the Eclipse resources API when generating the file on disk.
	 */
	// TODO: If the XML is large, this memory based approach is not the best choice. Keep an eye on this.
	private InputStream writeToXML(MetaModelHolder holder, Set<String> categories, boolean autopilotMode, MemberKindIndex availableMemberKinds)
	{
		//Writer writer = null;
		try
		{
//			File apiFile = new File("servoy_api.txt");
//			writer = new BufferedWriter(new FileWriter(apiFile));
//			System.out.println("writing api file:" + apiFile.getAbsolutePath());
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			LogUtil.logger().fine("Using document builder '" + builder.getClass().getCanonicalName() + "'.");
			Document doc = builder.newDocument();

			Element docRoot = doc.createElement(TAG_SERVOYDOC);
			if (autopilotMode)
			{
				Element extensionRoot = doc.createElement(TAG_SERVOYEXTENSION);
				doc.appendChild(extensionRoot);
				extensionRoot.appendChild(docRoot);
			}
			else
			{
				doc.appendChild(docRoot);
			}

			// If no category was specified, then use all categories that are present.
			if (categories == null)
			{
				categories = new TreeSet<String>();
				for (TypeMetaModel cls : holder.getSortedTypes())
				{
					if (cls.isServoyDocumented())
					{
						categories.add(cls.getCategory());
					}
				}
			}
			for (String category : categories)
			{
				Element catRoot = doc.createElement(category);
				docRoot.appendChild(catRoot);
				for (TypeMetaModel typeMM : holder.getSortedTypes())
				{
					if (typeMM.isServoyDocumented() && category.equals(typeMM.getCategory()))
					{
						catRoot.appendChild(toXML(typeMM, doc, false, availableMemberKinds, holder));
					}
				}
			}

			DOMSource source = new DOMSource(doc);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", new Integer(2));
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.transform(source, new StreamResult(new OutputStreamWriter(baos, "utf-8")));
			baos.close();

			return new ByteArrayInputStream(baos.toByteArray());
		}
		catch (Exception e)
		{
			LogUtil.logger().log(Level.SEVERE, "Exception while generating documentation.", e);
			return null;
		}
	}

	/**
	 * Returns an XML element that corresponds to this class.
	 */
	private Element toXML(TypeMetaModel typeMM, Document doc, boolean hideDeprecated, MemberKindIndex mk, MetaModelHolder holder)
	{
		Element objElement = doc.createElement(TAG_OBJECT);
		TypeStoragePlace typeData = (TypeStoragePlace)typeMM.getStore().get(STORE_KEY);
		if (typeMM.getPublicName() != null && typeMM.getPublicName().trim().length() > 0)
		{
			objElement.setAttribute(ATTR_PUBLICNAME, typeMM.getPublicName());
		}
		if (typeMM.getScriptingName() != null && typeMM.getScriptingName().trim().length() > 0)
		{
			objElement.setAttribute(ATTR_SCRIPTINGNAME, typeMM.getScriptingName());
		}
		objElement.setAttribute(ATTR_QUALIFIEDNAME, typeMM.getName().getQualifiedName());
		if (typeMM.isDeprecated())
		{
			objElement.setAttribute(ATTR_DEPRECATED, Boolean.TRUE.toString());
		}

		if (typeData.getExtendsComponent() != null && typeData.getExtendsComponent().trim().length() > 0)
		{
			objElement.setAttribute(ATTR_EXTENDSCOMPONENT, typeData.getExtendsComponent());
		}

		ClientSupport scp = typeMM.getServoyClientSupport(holder);
		ClientSupport unionedScp = scp;
		for (String kind : mk.getKinds())
		{
			ClientSupport membersUnionedScp = putMembersByType(typeMM, kind, doc, objElement, mk.getWrapperTag(kind), hideDeprecated, holder, scp);
			unionedScp = unionedScp == null ? membersUnionedScp : unionedScp.union(membersUnionedScp);
		}

		objElement.setAttribute(ATTR_CLIENT_SUPPORT, (unionedScp == null ? ClientSupport.Default : unionedScp).toAttribute());

		return objElement;
	}

	/**
	 * Add to the generated XML all members of a certain type (constructor, constants, properties, functions).
	 * @return unioned ClientSupport from type and members.
	 */
	private ClientSupport putMembersByType(TypeMetaModel typeMM, String kind, Document doc, Element objElement, String holderName, boolean hideDeprecated,
		MetaModelHolder holder, ClientSupport typeScp)
	{
		ClientSupport unionedScp = typeScp;
		Map<String, Element> children = new TreeMap<String, Element>();
		for (IMemberMetaModel memberMM : typeMM.getMembers(holder))
		{
			MemberStoragePlace memberData = (MemberStoragePlace)memberMM.getStore().get(STORE_KEY);
			if (kind.equals(memberData.getKind()) && memberData.shouldShow(typeMM) && (!memberMM.isDeprecated() || !hideDeprecated))
			{
				ClientSupport memberScp = memberData.getServoyClientSupport();
				if (memberScp != null)
				{
					if (typeScp != null && memberScp != typeScp && !typeScp.supports(ClientSupport.mc) && memberScp.supports(ClientSupport.mc))
					{
						// when type is not mobile, do not mark element as mobile
						memberScp = ClientSupport.create(false, memberScp.supports(ClientSupport.wc), memberScp.supports(ClientSupport.sc));
					}
					unionedScp = memberScp.union(unionedScp);
				}

				Element child = memberData.toXML(doc, includeSample(), memberScp == null ? ClientSupport.Default : memberScp);

				children.put(memberData.getOfficialSignature(), child);
			}
		}

		if (children.size() > 0)
		{
			Element propertiesElement = doc.createElement(holderName);
			objElement.appendChild(propertiesElement);
			for (Element el : children.values())
			{
				propertiesElement.appendChild(el);
			}
		}

		return unionedScp;
	}

	private void collectAllWarnings(MetaModelHolder holder, Set<DocumentationWarning> allWarnings)
	{
		for (TypeMetaModel typeMM : holder.getTypes())
		{
			allWarnings.addAll(typeMM.getWarnings());

			for (IMemberMetaModel memberMM : typeMM.getMembers())
			{
				allWarnings.addAll(memberMM.getWarnings());
			}
		}
	}
}
