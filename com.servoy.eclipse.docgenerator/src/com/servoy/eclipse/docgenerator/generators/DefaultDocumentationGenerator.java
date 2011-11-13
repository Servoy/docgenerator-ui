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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.FieldMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel.Visibility;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.MethodMetaModel;
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
	protected static final String STORE_KEY = DefaultDocumentationGenerator.class.getCanonicalName();

	// tags and attributes used in XML
	private static final String TAG_SERVOYEXTENSION = "servoyextension";
	private static final String TAG_SERVOYDOC = "servoydoc";
	// for class level
	private static final String TAG_OBJECT = "object";
	private static final String ATTR_PUBLICNAME = "publicName";
	private static final String ATTR_SCRIPTINGNAME = "scriptingName";
	private static final String ATTR_QUALIFIEDNAME = "qualifiedName";
	private static final String ATTR_EXTENDSCOMPONENT = "extendsComponent";
	private static final String ATTR_DEPRECATED = "deprecated";
	public static final String TAG_CONSTANTS = "constants";
	public static final String TAG_PROPERTIES = "properties";
	public static final String TAG_CONSTRUCTORS = "constructors";
	public static final String TAG_FUNCTIONS = "functions";
	// for members
	private static final String ATTR_UNDOCUMENTED = "undocumented";
	private static final String TAG_DEPRECATED = "deprecated";
	private static final String TAG_URL = "url";
	private static final String TAG_LINK = "link";
	private static final String TAG_LINKS = "links";
	private static final String TAG_SAMPLE = "sample";
	private static final String TAG_SUMMARY = "summary";
	private static final String TAG_DESCRIPTION = "description";
	private static final String ATTR_SINCE = "since";
	private static final String ATTR_UNTIL = "until";
	private static final String ATTR_SPECIAL = "special";
	private static final String ATTR_NAME = "name";
	protected static final String ATTR_TYPECODE = "typecode";
	protected static final String ATTR_TYPE = "type";
	protected static final String TAG_RETURN = "return";
	// for methods
	public static final String TAG_FUNCTION = "function";
	public static final String TAG_PROPERTY = "property";
	public static final String TAG_CONSTRUCTOR = "constructor";
	private static final String TAG_PARAMETER = "parameter";
	private static final String TAG_PARAMETERS = "parameters";
	private static final String TAG_ARGUMENT_TYPE = "argumentType";
	private static final String TAG_ARGUMENTS_TYPES = "argumentsTypes";
	private static final String ATTR_VARARGS = "varargs";
	// for fields
	public static final String TAG_CONSTANT = "constant";

	public static final String ANNOTATION_JS_READONLY_PROPERTY = "JSReadonlyProperty";
	public static final String ANNOTATION_JS_FUNCTION = "JSFunction";
	// prefixes for marking methods that should show up in JavaScript
	public static final String JS_PREFIX = "js_";
	public static final String JS_FUNCTION_PREFIX = "jsFunction_";
	public static final String JS_CONSTRUCTOR_PREFIX = "jsConstructor_";

	private final Set<String> solved = new HashSet<String>();
	protected final Set<String> typesMapped = new HashSet<String>();

	public String getID()
	{
		return STORE_KEY;
	}

	public InputStream generate(DocumentationGenerationRequest req, MetaModelHolder holder, Set<DocumentationWarning> allWarnings)
	{
		FinalProcessor fp = new FinalProcessor(req.tryToMapUndocumentedTypes());
		IMemberDataFactory mdFactory = getDataFactory(holder, fp);
		assignDocs(holder, mdFactory);
		solveDependencies(holder);
		reassignDocs(holder);
		typesMapped.clear();
		processTypes(holder, fp);
		req.postProcess(holder);

		return writeToXML(holder, req.getCategoryFilter(), allWarnings, req.autopilot());
	}


	protected void reassignDocs(MetaModelHolder holder)
	{
		// nothing here
	}

	protected IMemberDataFactory getDataFactory(MetaModelHolder holder, FinalProcessor fp)
	{
		return new DefaultMemberDataFactory();
	}

	/**
	 * Create for every type and member a storage place where relevant information can be
	 * distilled.
	 */
	protected void assignDocs(MetaModelHolder holder, IMemberDataFactory mdFactory)
	{
		for (TypeMetaModel typeMM : holder.values())
		{
			if (!typeMM.getStore().containsKey(STORE_KEY))
			{
				TypeDataRaw typeData = new TypeDataRaw(typeMM);
				typeMM.getStore().put(STORE_KEY, typeData);
			}

			for (MemberMetaModel memberMM : typeMM.values())
			{
				if (!memberMM.getStore().containsKey(STORE_KEY))
				{
					MemberDataRaw memberData = mdFactory.getData(typeMM, memberMM);
					memberMM.getStore().put(STORE_KEY, memberData);
				}
			}
		}
	}

	/**
	 * Solved dependencies, namely the @sameas, @sampleas and @clonedesc tags used inside Javadocs.
	 * Also copies documentation from interfaces to implementing classes, if needed.
	 */
	protected void solveDependencies(MetaModelHolder holder)
	{
		for (TypeMetaModel typeMM : holder.values())
		{
			// try to fix the "extendsComponent", in case it holds just a public name and not a qualified name
			TypeDataRaw typeData = (TypeDataRaw)typeMM.getStore().get(STORE_KEY);
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
			for (MemberMetaModel member : typeMM.values())
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
			for (MemberMetaModel memberMM : typeMM.values())
			{
				MemberMetaModel mine = target.get(memberMM.getIndexSignature());
				MemberDataRaw myData = (MemberDataRaw)mine.getStore().get(STORE_KEY);
				MemberDataRaw theirData = (MemberDataRaw)memberMM.getStore().get(STORE_KEY);
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
	private void solveDependenciesForMember(MemberMetaModel memberMM, MetaModelHolder holder, Stack<String> visited)
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
				MemberDataRaw memberData = (MemberDataRaw)memberMM.getStore().get(STORE_KEY);
				DocumentationDataRaw docData = memberData.getDocData();
				if (docData != null)
				{
					if (docData.getSameAs() != null)
					{
						if (docData.getCloneSample() != null)
						{
							memberMM.getWarnings().add(
								new DocumentationWarning(WarningType.RedirectionProblem, fullSig, "The " + DocumentationDataRaw.TAG_SAMPLE_AS +
									" tag is ignored because it is used together with " + DocumentationDataRaw.TAG_SAMEAS + "."));
						}
						if (docData.getCloneDescription() != null)
						{
							memberMM.getWarnings().add(
								new DocumentationWarning(WarningType.RedirectionProblem, fullSig, "The " + DocumentationDataRaw.TAG_CLONEDESC +
									" tag is ignored because it is used together with " + DocumentationDataRaw.TAG_SAMEAS + "."));
						}
						MemberMetaModel target = find(holder, memberMM, docData.getSameAs(), true);
						if (target != null)
						{
							solveDependenciesForMember(target, holder, visited);
							MemberDataRaw targetData = (MemberDataRaw)target.getStore().get(STORE_KEY);
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
							MemberMetaModel target = find(holder, memberMM, docData.getCloneSample(), true);
							if (target != null)
							{
								solveDependenciesForMember(target, holder, visited);
								MemberDataRaw targetData = (MemberDataRaw)target.getStore().get(STORE_KEY);
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
							MemberMetaModel target = find(holder, memberMM, docData.getCloneDescription(), true);
							if (target != null)
							{
								solveDependenciesForMember(target, holder, visited);
								MemberDataRaw targetData = (MemberDataRaw)target.getStore().get(STORE_KEY);
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
				// If no documentation, look into superclass and interfaces.
//				else
//				{
//					TypeMetaModel owner = holder.get(memberMM.getClassName());
//					if (owner.getSupertype() != null)
//					{
//						copyFromAbove(holder, memberMM, owner.getSupertype().getQualifiedName(), visited);
//					}
//					if (memberData.getDocData() == null)
//					{
//						for (TypeName s : owner.getInterfaces())
//						{
//							copyFromAbove(holder, memberMM, s.getQualifiedName(), visited);
//							if (memberData.getDocData() != null)
//							{
//								break;
//							}
//						}
//					}
//				}
				visited.pop();
			}
			solved.add(memberMM.getFullSignature());
		}
	}

	/**
	 * Finds a specific member from a specific class, if it exists. Optionally raises a warning if the searched
	 * method or its parent class are not found.
	 */
	private MemberMetaModel find(MetaModelHolder holder, MemberMetaModel target, QualifiedNameRaw redirect, boolean reportMissing)
	{
		TypeMetaModel typeMM = holder.get(redirect.getClassName());
		if (typeMM != null)
		{
			MemberMetaModel memberMM = null;
			for (MemberMetaModel candidate : typeMM.values())
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

	/**
	 * Copies the documentation from another class (superclass or implemented interface).
	 */
//	private void copyFromAbove(MetaModelHolder holder, MemberMetaModel memberMM, String aboveName, Stack<String> visited)
//	{
//		QualifiedNameRaw qname = new QualifiedNameRaw(aboveName, memberMM.getIndexSignature(), null);
//		MemberMetaModel target = find(holder, memberMM, qname, false);
//		if (target != null && target.getStore().containsKey(STORE_KEY))
//		{
//			solveDependenciesForMember(target, holder, visited);
//			MemberDataRaw targetData = (MemberDataRaw)target.getStore().get(STORE_KEY);
//			if (targetData.getDocData() == null)
//			{
//				DocumentationWarning dw = new DocumentationWarning(WarningType.RedirectionProblem, memberMM.getFullSignature(),
//					"Target of redirection is not documented: " + qname.toString());
//				memberMM.getWarnings().add(dw);
//			}
//			else
//			{
//				MemberDataRaw memberData = (MemberDataRaw)memberMM.getStore().get(STORE_KEY);
//				memberData.setDocData(targetData.getDocData());
//			}
//		}
//	}

	/**
	 * Generates a memory based stream with the content of the XML file. The generated stream
	 * is fed to the Eclipse resources API when generating the file on disk.
	 */
	// TODO: If the XML is large, this memory based approach is not the best choice. Keep an eye on this.
	public InputStream writeToXML(MetaModelHolder holder, Set<String> categories, Set<DocumentationWarning> allWarnings, boolean autopilotMode)
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
						Element el = toXML(typeMM, doc, false, allWarnings);
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
	private Element toXML(TypeMetaModel typeMM, Document doc, boolean hideDeprecated, Set<DocumentationWarning> allWarnings)
	{
		allWarnings.addAll(typeMM.getWarnings());

		Element objElement = doc.createElement(TAG_OBJECT);
		TypeDataRaw typeData = (TypeDataRaw)typeMM.getStore().get(STORE_KEY);
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

		MemberKind mk = getMemberKind();
		List<String> kinds = mk.getKinds();
		for (String kind : kinds)
		{
			putMembersByType(typeMM, kind, doc, objElement, mk.getHolderTag(kind), hideDeprecated, allWarnings);
		}

		return objElement;
	}

	protected MemberKind getMemberKind()
	{
		return new MemberKind();
	}

	/**
	 * Add to the generated XML all members of a certain type (constructor, constants, properties, functions).
	 */
	private void putMembersByType(TypeMetaModel typeMM, String kind, Document doc, Element objElement, String holderName, boolean hideDeprecated,
		Set<DocumentationWarning> allWarnings)
	{
		Map<String, Element> children = new TreeMap<String, Element>();
		for (MemberMetaModel memberMM : typeMM.values())
		{
			MemberDataRaw memberData = (MemberDataRaw)memberMM.getStore().get(STORE_KEY);
			if (memberData.getKind() == kind && shouldShow(typeMM, memberMM) && (!memberMM.isDeprecated() || !hideDeprecated))
			{
				Element child;
				if (memberMM instanceof MethodMetaModel)
				{
					child = toXML((MethodMetaModel)memberMM, doc, allWarnings);
				}
				else
				{
					child = toXML((FieldMetaModel)memberMM, doc, allWarnings);
				}
				String sig = getOfficialSignature(memberMM);
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

	protected boolean shouldShow(TypeMetaModel typeMM, MemberMetaModel memberMM)
	{
		if (memberMM instanceof FieldMetaModel)
		{
			FieldMetaModel fieldMM = (FieldMetaModel)memberMM;
			return fieldMM.getVisibility() == Visibility.Public && fieldMM.isStatic();
		}
		else if (memberMM instanceof MethodMetaModel)
		{
			MethodMetaModel methodMM = (MethodMetaModel)memberMM;

			// if it's private, don't show
			if (methodMM.getVisibility() == Visibility.Private)
			{
				return false;
			}
			// if it's static, don't show
			if (methodMM.isStatic())
			{
				return false;
			}

			// if it's annotated properly, then it should show
			if (methodMM.getAnnotations().hasAnnotation(ANNOTATION_JS_FUNCTION) || methodMM.getAnnotations().hasAnnotation(ANNOTATION_JS_READONLY_PROPERTY))
			{
				return true;
			}

			if (methodMM.getName().startsWith(JS_PREFIX))
			{
				String shortName = methodMM.getName().substring(JS_PREFIX.length());
				// setters from properties should not show
				if (shortName.startsWith("set") && methodMM.getType() != null && methodMM.getType().getQualifiedName().equals(void.class.getSimpleName()))
				{
					if (methodMM.getParameters().size() == 1)
					{
						String pair = JS_PREFIX + "get" + shortName.substring("set".length()) + "()";
						MemberMetaModel getter = null;
						if (typeMM.containsKey(pair))
						{
							getter = typeMM.get(pair);
						}
						else
						{
							pair = JS_PREFIX + "is" + shortName.substring("set".length()) + "()";
							if (typeMM.containsKey(pair))
							{
								getter = typeMM.get(pair);
							}
						}
						if (getter != null)
						{
							if (getter instanceof MethodMetaModel)
							{
								Iterator<TypeName> it = methodMM.getParameters().values().iterator();
								TypeName par = it.next();
								MethodMetaModel getterMeth = (MethodMetaModel)getter;
								if (getterMeth.getType() != null && getterMeth.getType().getQualifiedName().equals(par.getQualifiedName()))
								{
									return false;
								}
								// special case when the setter has an Object parameter
								if (par.getQualifiedName().endsWith("." + Object.class.getSimpleName()))
								{
									return false;
								}
							}
						}
					}
				}
				return true;
			}
			else if (methodMM.getName().startsWith(JS_FUNCTION_PREFIX))
			{
				return true;
			}
			else if (methodMM.getName().startsWith(JS_CONSTRUCTOR_PREFIX))
			{
				return true;
			}
			return false;
		}
		return false;
	}

	public String getOfficialSignature(MemberMetaModel memberMM)
	{
		if (memberMM instanceof MethodMetaModel)
		{
			MethodMetaModel methodMM = (MethodMetaModel)memberMM;
			MemberDataRaw memberData = (MemberDataRaw)methodMM.getStore().get(STORE_KEY);
			int paren = methodMM.getIndexSignature().indexOf("(");
			return memberData.getOfficialName() + methodMM.getIndexSignature().substring(paren);
		}
		else
		{
			FieldMetaModel fmm = (FieldMetaModel)memberMM;
			return fmm.getIndexSignature();
		}
	}

	protected boolean includeSample()
	{
		return true;
	}

	/**
	 * Returns an XML element with information about this member.
	 */
	public Element toXMLMember(MemberMetaModel memberMM, String tagName, Document domDoc, Set<DocumentationWarning> allWarnings)
	{
		// Add all local warnings to the global set of warnings.
		allWarnings.addAll(memberMM.getWarnings());
		MemberDataRaw memberData = (MemberDataRaw)memberMM.getStore().get(STORE_KEY);

		Element root = domDoc.createElement(tagName);
		root.setAttribute(ATTR_NAME, memberData.getOfficialName());
		if (memberData.isDeprecated())
		{
			root.setAttribute(ATTR_DEPRECATED, Boolean.TRUE.toString());
		}
		DocumentationDataRaw ddr = memberData.getDocData();
		if (ddr != null && ddr.hasDocumentation())
		{
			if (ddr.isSpecial())
			{
				root.setAttribute(ATTR_SPECIAL, Boolean.TRUE.toString());
			}
			Element descr = domDoc.createElement(TAG_DESCRIPTION);
			root.appendChild(descr);
			if (ddr.getText() != null)
			{
				descr.appendChild(domDoc.createCDATASection(ddr.getText().trim()));
			}
			if (ddr.getSummary() != null && ddr.getSummary().trim().length() > 0)
			{
				Element summary = domDoc.createElement(TAG_SUMMARY);
				root.appendChild(summary);
				summary.appendChild(domDoc.createCDATASection(ddr.getSummary().trim()));
			}
			if (ddr.getDeprecatedText() != null && ddr.getDeprecatedText().trim().length() > 0)
			{
				Element deprecatedElement = domDoc.createElement(TAG_DEPRECATED);
				root.appendChild(deprecatedElement);
				deprecatedElement.appendChild(domDoc.createCDATASection(ddr.getDeprecatedText().trim()));
			}
			if (includeSample())
			{
				Element sample = domDoc.createElement(TAG_SAMPLE);
				root.appendChild(sample);
				if (ddr.getSample() != null && ddr.getSample().trim().length() > 0)
				{
					sample.appendChild(domDoc.createCDATASection(ddr.getSample().trim()));
				}
			}
			if (ddr.getLinks() != null && ddr.getLinks().size() > 0)
			{
				Element links = domDoc.createElement(TAG_LINKS);
				for (String lnk : ddr.getLinks())
				{
					Element elLink = domDoc.createElement(TAG_LINK);
					Element elUrl = domDoc.createElement(TAG_URL);
					elUrl.appendChild(domDoc.createTextNode(lnk));
					elLink.appendChild(elUrl);
					links.appendChild(elLink);
				}
				root.appendChild(links);
			}
			if (ddr.getSince() != null && ddr.getSince().trim().length() > 0)
			{
				root.setAttribute(ATTR_SINCE, ddr.getSince());
			}
			if (ddr.getUntil() != null && ddr.getUntil().trim().length() > 0)
			{
				root.setAttribute(ATTR_UNTIL, ddr.getUntil());
			}
		}
		else
		{
			root.setAttribute(ATTR_UNDOCUMENTED, Boolean.TRUE.toString());
		}
		return root;
	}

	protected boolean hideParametersForKind(String kind)
	{
		return !(TAG_CONSTRUCTOR.equals(kind) || TAG_FUNCTION.equals(kind));
	}

	protected boolean hideReturnType(String kind)
	{
		return TAG_CONSTRUCTOR.equals(kind);
	}

	public Element toXML(MethodMetaModel methdr, Document doc, Set<DocumentationWarning> allWarnings)
	{
		MemberDataRaw memberData = (MemberDataRaw)methdr.getStore().get(STORE_KEY);
		String kind = memberData.getKind();
		Element root = toXMLMember(methdr, kind, doc, allWarnings);
		if (methdr.isVarargs())
		{
			root.setAttribute(ATTR_VARARGS, Boolean.TRUE.toString());
		}
		DocumentationDataRaw ddr = memberData.getDocData();
		if (!hideReturnType(kind) && memberData.getType() != null)
		{
			Element retType = doc.createElement(TAG_RETURN);
			retType.setAttribute(ATTR_TYPE, memberData.getType().getQualifiedName());
			retType.setAttribute(ATTR_TYPECODE, memberData.getType().getBinaryName());
			if (ddr != null && ddr.getReturn() != null && ddr.getReturn().trim().length() > 0)
			{
				retType.appendChild(doc.createCDATASection(ddr.getReturn().trim()));
			}
			root.insertBefore(retType, root.getFirstChild());
		}
		if (!hideParametersForKind(kind))
		{
			Element argTypes = doc.createElement(TAG_ARGUMENTS_TYPES);
			for (String parName : methdr.getParameters().keySet())
			{
				TypeName parType = methdr.getParameters().get(parName);
				Element argType = doc.createElement(TAG_ARGUMENT_TYPE);
				argTypes.appendChild(argType);
				argType.setAttribute(ATTR_TYPECODE, parType.getBinaryName());
			}
			root.insertBefore(argTypes, root.getFirstChild());
		}
		boolean showParameters = false;
		if (ddr != null && ddr.hasDocumentation())
		{
			showParameters = ddr.getParameters().size() > 0;
		}
		else
		{
			showParameters = methdr.getParameters().size() > 0;
		}
		if (showParameters)
		{
			Element paramDocs = doc.createElement(TAG_PARAMETERS);
			NodeList existing = root.getChildNodes();
			boolean was = false;
			Node place = null;
			// try to place the parameters right after the sample
			for (int i = 0; i < existing.getLength(); i++)
			{
				Node ex = existing.item(i);
				if (was)
				{
					place = ex;
					break;
				}
				if ("sample".equals(ex.getNodeName()))
				{
					was = true;
				}
			}
			if (place == null)
			{
				root.appendChild(paramDocs);
			}
			else
			{
				root.insertBefore(paramDocs, place);
			}
			if (ddr != null && ddr.hasDocumentation())
			{
				for (ParamDataRaw par : ddr.getParameters())
				{
					String parName = par.getName();
					Element elPar = par.toXML(doc);
					TypeName parType = null;
					// If the parameter name is from the method signature, then grab the real type.
					if (memberData.getParameters().containsKey(parName))
					{
						parType = memberData.getParameters().get(parName);
					}
					// Otherwise try to grab the type suggested by the user (if any and if valid).
					else if (par.getType() != null)
					{
						parType = par.getType();
					}
					if (parType != null)
					{
						elPar.setAttribute(ATTR_TYPE, parType.getQualifiedName());
						elPar.setAttribute(ATTR_TYPECODE, parType.getBinaryName());
					}
					paramDocs.appendChild(elPar);
				}
			}
			// for undocumented functions we still list the parameters and their types
			else
			{
				for (String parName : memberData.getParameters().keySet())
				{
					TypeName parType = memberData.getParameters().get(parName);
					Element elPar = doc.createElement(TAG_PARAMETER);
					elPar.setAttribute(ATTR_NAME, parName);
					elPar.setAttribute(ATTR_TYPE, parType.getQualifiedName());
					elPar.setAttribute(ATTR_TYPECODE, parType.getBinaryName());
					paramDocs.appendChild(elPar);
				}
			}
		}
		return root;
	}

	public Element toXML(FieldMetaModel fieldMM, Document doc, Set<DocumentationWarning> allWarnings)
	{
		Element root = toXMLMember(fieldMM, TAG_CONSTANT, doc, allWarnings);
		MemberDataRaw memberData = (MemberDataRaw)fieldMM.getStore().get(STORE_KEY);
		if (memberData.getType() != null)
		{
			Element retType = doc.createElement(TAG_RETURN);
			retType.setAttribute(ATTR_TYPE, memberData.getType().getQualifiedName());
			retType.setAttribute(ATTR_TYPECODE, memberData.getType().getBinaryName());
			DocumentationDataRaw ddr = memberData.getDocData();
			if (ddr != null && ddr.getReturn() != null && ddr.getReturn().trim().length() > 0)
			{
				retType.appendChild(doc.createCDATASection(ddr.getReturn().trim()));
			}
			root.insertBefore(retType, root.getFirstChild());
		}
		return root;
	}

	public void processTypes(MetaModelHolder holder, FinalProcessor fp)
	{
		for (TypeMetaModel typeMM : holder.values())
		{
			for (MemberMetaModel memberMM : typeMM.values())
			{
				MemberDataRaw memberData = (MemberDataRaw)memberMM.getStore().get(DefaultDocumentationGenerator.STORE_KEY);
				DocumentationDataRaw doc = memberData.getDocData();
				if (doc != null)
				{
					if (doc.getParameters() != null)
					{
						for (ParamDataRaw par : doc.getParameters())
						{
							par.checkIfHasType(holder, fp);
						}
					}
				}

				mapTypes(holder, fp, memberMM);
			}
		}
	}

	private void mapTypes(MetaModelHolder holder, FinalProcessor proc, MemberMetaModel member)
	{
		if (!typesMapped.contains(member.getFullSignature()))
		{
			mapTypesInternal(holder, proc, member);
			typesMapped.add(member.getFullSignature());
		}
	}

	private void mapTypesInternal(MetaModelHolder holder, FinalProcessor proc, MemberMetaModel member)
	{
		MemberDataRaw memberData = (MemberDataRaw)member.getStore().get(STORE_KEY);
		TypeName newTN = proc.mapType(holder, memberData.getType(), false, new boolean[1]);
		memberData.setType(newTN);
		if (member instanceof FieldMetaModel)
		{
			FieldMetaModel fieldMM = (FieldMetaModel)member;
		}
		else if (member instanceof MethodMetaModel)
		{
			MethodMetaModel methodMM = (MethodMetaModel)member;
			for (String parName : methodMM.getParameters().keySet())
			{
				TypeName parData = methodMM.getParameters().get(parName);
				boolean[] flag = new boolean[1];
				TypeName nw = proc.mapType(holder, parData, false, flag);
				if (flag[0])
				{
					memberData.getParameters().put(parName, nw);
				}
				else
				{
					memberData.getParameters().put(parName, parData);
				}
			}

		}
	}

}
