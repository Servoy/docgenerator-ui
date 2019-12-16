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

package com.servoy.eclipse.docgenerator.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Level;
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

import com.servoy.eclipse.docgenerator.generators.AbstractDocumentationGenerator;
import com.servoy.eclipse.docgenerator.generators.ExtractorUtil;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocTagPart;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;
import com.servoy.j2db.persistence.ArgumentType;
import com.servoy.j2db.persistence.MethodArgument;
import com.servoy.j2db.persistence.MethodTemplate;

/**
 * @author gerzse
 */

public class MethodTemplatesXmlGenerator extends AbstractDocumentationGenerator
{
	private static final String TAG_TEMPLATE_NAME = "@templatename";
	public static final String TAG_TEMPLATE_TYPE = "@templatetype";
	private static final String TAG_TEMPLATE_PRIVATE = "@templateprivate";
	private static final String TAG_TEMPLATE_DESCRIPTION = "@templatedescription";
	public static final String TAG_TEMPLATE_PARAM = "@templateparam";
	private static final String TAG_TEMPLATE_ADD_TODO = "@templateaddtodo";
	private static final String TAG_TEMPLATE_CODE = "@templatecode";

	public String getID()
	{
		return getClass().getSimpleName();
	}

	public InputStream generate(DocumentationGenerationRequest req, MetaModelHolder holder, Set<DocumentationWarning> allWarnings, IPath actualOutputFile)
	{
		try
		{
			TreeMap<String, MethodTemplate> templates = new TreeMap<String, MethodTemplate>();
			for (TypeMetaModel typeMM : holder.getTypes())
			{
				for (IMemberMetaModel memberMM : typeMM.getMembers(holder))
				{
					JavadocMetaModel javadoc = memberMM.getJavadoc(holder);
					if (javadoc != null && javadoc.findTags(TAG_TEMPLATE_NAME).size() > 0)
					{
						extractMethodTemplate(memberMM, templates, javadoc);
					}
				}
			}

			return generateContent(templates);
		}
		catch (Throwable e)
		{
			LogUtil.logger().log(Level.SEVERE, "Exception while generating documentation.", e);
			return null;
		}
	}

	private void extractMethodTemplate(IMemberMetaModel memberMM, TreeMap<String, MethodTemplate> templates, JavadocMetaModel jdoc)
	{
		Set<DocumentationWarning> warnings = memberMM.getWarnings();
		String location = memberMM.getFullSignature();

		boolean clean = true;

		String name = ExtractorUtil.grabExactlyOne(TAG_TEMPLATE_NAME, clean, jdoc, warnings, location);
		String description = ExtractorUtil.grabExactlyOne(TAG_TEMPLATE_DESCRIPTION, clean, jdoc, warnings, location);

		String templateType = ExtractorUtil.grabExactlyOne(TAG_TEMPLATE_TYPE, clean, jdoc, warnings, location);
		ArgumentType templType = null;
		if (templateType != null)
		{
			templType = ArgumentType.valueOf(templateType);
		}

		JavadocTagPart todoTag = ExtractorUtil.grabFirstTag(TAG_TEMPLATE_ADD_TODO, jdoc, true, warnings, location);
		boolean addTodo = (todoTag != null);

		String code = ExtractorUtil.grabExactlyOne(TAG_TEMPLATE_CODE, clean, jdoc, warnings, location);
		if (code != null)
		{
			// add back the "*/"
			code = Pattern.compile("\\*&#47;").matcher(code).replaceAll("*/");
		}

		List<MethodArgument> arguments = new ArrayList<MethodArgument>();
		List<JavadocTagPart> paramTags = jdoc.findTags(TAG_TEMPLATE_PARAM);
		for (JavadocTagPart paramTag : paramTags)
		{
			String allText = paramTag.getAsString(clean);
			StringTokenizer tok = new StringTokenizer(allText);
			String argTypeStr = tok.nextToken();
			ArgumentType argType = ArgumentType.valueOf(argTypeStr);
			String argName = tok.nextToken();
			String argDesc = allText.substring(allText.indexOf(argName) + argName.length()).trim();
			MethodArgument arg = new MethodArgument(argName, argType, argDesc);
			arguments.add(arg);
		}
		MethodArgument[] args = null;
		if (arguments.size() > 0)
		{
			args = new MethodArgument[arguments.size()];
			arguments.toArray(args);
		}

		JavadocTagPart privateTag = ExtractorUtil.grabFirstTag(TAG_TEMPLATE_PRIVATE, jdoc, true, warnings, location);
		boolean privateMethod = (privateTag != null);

		String niceName = memberMM.getName();
		if (niceName.startsWith("get"))
		{
			niceName = niceName.substring("get".length(), "get".length() + 1).toLowerCase() + niceName.substring("get".length() + 1);
		}

		MethodTemplate templ = new MethodTemplate(description, new MethodArgument(name, templType, null), args, code, addTodo);
		templ.setPrivateMethod(privateMethod);

		templates.put(niceName, templ);
	}

	protected InputStream generateContent(TreeMap<String, MethodTemplate> templates) throws Exception
	{
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.newDocument();

		Element docRoot = doc.createElement("root");
		doc.appendChild(docRoot);

		for (String name : templates.keySet())
		{
			MethodTemplate template = templates.get(name);
			Element event = doc.createElement("event");
			docRoot.appendChild(event);
			event.setAttribute("name", name);
			event.appendChild(template.toXML(doc));
		}

		DOMSource source = new DOMSource(doc);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setAttribute("indent-number", new Integer(2));
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "no");
		transformer.transform(source, new StreamResult(new OutputStreamWriter(baos, "utf-8")));
		baos.close();

		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		return bais;
	}
}
