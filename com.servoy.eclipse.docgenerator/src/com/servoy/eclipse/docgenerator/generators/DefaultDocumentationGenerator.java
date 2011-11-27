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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.runtime.IPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

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
		IStoragePlaceFactory storageFactory = getDataFactory(holder, typeMapper);
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
	protected IStoragePlaceFactory getDataFactory(MetaModelHolder holder, TypeMapper typeMapper)
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
		for (TypeMetaModel typeMM : holder.values())
		{
			if (!typeMM.getStore().containsKey(STORE_KEY))
			{
				TypeStoragePlace typeData = new TypeStoragePlace(typeMM);
				typeMM.getStore().put(STORE_KEY, typeData);
			}

			for (IMemberMetaModel memberMM : typeMM.values())
			{
				if (!memberMM.getStore().containsKey(STORE_KEY))
				{
					MemberStoragePlace memberData = mdFactory.getData(typeMM, memberMM);
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
		for (TypeMetaModel typeMM : holder.values())
		{
			// try to fix the "extendsComponent", in case it holds just a public name and not a qualified name
			TypeStoragePlace typeData = (TypeStoragePlace)typeMM.getStore().get(STORE_KEY);
			if (typeData.getExtendsComponent() != null)
			{
				if (!holder.containsKey(typeData.getExtendsComponent()))
				{
					for (TypeMetaModel candidate : holder.values())
					{
						if (typeData.getExtendsComponent().equals(candidate.getPublicName()))
						{
							typeData.setExtendsComponent(candidate.getName().getQualifiedName());
							break;
						}
					}
				}
			}

			// copy any missing documentation from superclass or interfaces
			copyMissingDocsFromAbove(holder, typeMM, typeMM);

			// solve @sameas, @sampleas and @clonedesc references
			for (IMemberMetaModel member : typeMM.values())
			{
				solveDependenciesForMember(member, holder, new Stack<String>());
			}
		}
	}

	/**
	 * Copies recursively from the superclass and interfaces any method that is not defined
	 * in this class, or that is defined but does not have any Javadoc.
	 */
	private void copyMissingDocsFromAbove(MetaModelHolder holder, TypeMetaModel current, TypeMetaModel target)
	{
		List<TypeMetaModel> sources = new ArrayList<TypeMetaModel>();
		if (current.getSupertype() != null)
		{
			TypeMetaModel sup = holder.get(current.getSupertype().getQualifiedName());
			if (sup != null)
			{
				sources.add(sup);
			}
		}
		for (TypeName interf : current.getInterfaces())
		{
			TypeMetaModel src = holder.get(interf.getQualifiedName());
			if (src != null)
			{
				sources.add(src);
			}
		}
		for (TypeMetaModel typeMM : sources)
		{
			for (IMemberMetaModel memberMM : typeMM.values())
			{
				IMemberMetaModel mine = target.get(memberMM.getIndexSignature());
				MemberStoragePlace myData = (MemberStoragePlace)mine.getStore().get(STORE_KEY);
				MemberStoragePlace theirData = (MemberStoragePlace)memberMM.getStore().get(STORE_KEY);
				if (myData.getDocData() == null && theirData.getDocData() != null)
				{
					myData.setDocData(theirData.getDocData());
				}
			}
		}
		for (TypeMetaModel src : sources)
		{
			copyMissingDocsFromAbove(holder, src, target);
		}
	}

	/**
	 * Solves dependencies between this member and any other member referenced from it.
	 * The @sameas, @sampleas and @clonedesc tags are considered when visiting references.
	 */
	private void solveDependenciesForMember(IMemberMetaModel memberMM, MetaModelHolder holder, Stack<String> visited)
	{
		// If already solved, do nothing.
		if (!solved.contains(memberMM.getFullSignature()))
		{
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
									docData.setSample(targetData.getDocData().getSample());
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
									docData.setText(targetData.getDocData().getText());
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
		TypeMetaModel typeMM = holder.get(redirect.getClassName());
		if (typeMM != null)
		{
			IMemberMetaModel memberMM = null;
			for (IMemberMetaModel candidate : typeMM.values())
			{
				if (candidate.matchesSignature(redirect.getMemberSignature()))
				{
					memberMM = candidate;
					break;
				}
			}
			if (memberMM != null)
			{
				return memberMM;
			}
			else
			{
				if (reportMissing)
				{
					target.getWarnings().add(
						new DocumentationWarning(WarningType.RedirectionProblem, target.getFullSignature(), "Cannot find target member of redirection: " +
							redirect.toString()));
				}
				return null;
			}
		}
		else
		{
			if (reportMissing)
			{
				target.getWarnings().add(
					new DocumentationWarning(WarningType.RedirectionProblem, target.getFullSignature(), "Cannot find target class of redirection: " +
						redirect.toString()));
			}
			return null;
		}
	}


	public void doTypeMappingForAll(MetaModelHolder holder, TypeMapper typeMapper)
	{
		for (TypeMetaModel typeMM : holder.values())
		{
			for (IMemberMetaModel memberMM : typeMM.values())
			{
				MemberStoragePlace memberData = (MemberStoragePlace)memberMM.getStore().get(DefaultDocumentationGenerator.STORE_KEY);
				DocumentationDataDistilled doc = memberData.getDocData();
				if (doc != null)
				{
					if (doc.getParameters() != null)
					{
						for (DocumentedParameterData par : doc.getParameters())
						{
							par.checkIfHasType(holder, typeMapper);
						}
					}
				}
				memberData.mapTypes(holder, typeMapper);
			}
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
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
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
						Element el = toXML(typeMM, doc, false, availableMemberKinds);
						catRoot.appendChild(el);
					}
				}
			}

			OutputFormat outformat = new OutputFormat(doc);
			outformat.setIndenting(true);
			outformat.setIndent(2);
			outformat.setLineWidth(0);
			PrintWriter wr = new PrintWriter(baos);
			XMLSerializer serializer = new XMLSerializer(wr, outformat);
			serializer.serialize(doc);
			wr.close();
			baos.close();

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			return bais;
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
	private Element toXML(TypeMetaModel typeMM, Document doc, boolean hideDeprecated, MemberKindIndex mk)
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

		List<String> kinds = mk.getKinds();
		for (String kind : kinds)
		{
			putMembersByType(typeMM, kind, doc, objElement, mk.getWrapperTag(kind), hideDeprecated);
		}

		return objElement;
	}

	/**
	 * Add to the generated XML all members of a certain type (constructor, constants, properties, functions).
	 */
	private void putMembersByType(TypeMetaModel typeMM, String kind, Document doc, Element objElement, String holderName, boolean hideDeprecated)
	{
		Map<String, Element> children = new TreeMap<String, Element>();
		for (IMemberMetaModel memberMM : typeMM.values())
		{
			MemberStoragePlace memberData = (MemberStoragePlace)memberMM.getStore().get(STORE_KEY);
			if (memberData.getKind() == kind && memberData.shouldShow(typeMM) && (!memberMM.isDeprecated() || !hideDeprecated))
			{
				Element child = memberData.toXML(doc, includeSample());
				String sig = memberData.getOfficialSignature();
				children.put(sig, child);
			}
		}
		if (children.size() > 0)
		{
			Element propertiesElement = doc.createElement(holderName);
			objElement.appendChild(propertiesElement);
			for (Element el : children.values())
				propertiesElement.appendChild(el);
		}
	}

	private void collectAllWarnings(MetaModelHolder holder, Set<DocumentationWarning> allWarnings)
	{
		for (TypeMetaModel typeMM : holder.values())
		{
			allWarnings.addAll(typeMM.getWarnings());

			for (IMemberMetaModel memberMM : typeMM.values())
			{
				allWarnings.addAll(memberMM.getWarnings());
			}
		}
	}
}