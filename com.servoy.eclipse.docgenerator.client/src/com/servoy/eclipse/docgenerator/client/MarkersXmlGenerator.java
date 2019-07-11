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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

import com.servoy.eclipse.docgenerator.generators.AbstractDocumentationGenerator;
import com.servoy.eclipse.docgenerator.generators.ExtractorUtil;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.FieldMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;
import com.servoy.eclipse.model.builder.MarkerMessages.ServoyMarker;

/**
 * @author gerzse
 */

public class MarkersXmlGenerator extends AbstractDocumentationGenerator
{
	private static Class< ? > MARKERS_HOLDER_CLASS = com.servoy.eclipse.model.builder.MarkerMessages.class;
	private static Class< ? > SERVOY_MARKER_CLASS = com.servoy.eclipse.model.builder.MarkerMessages.ServoyMarker.class;

	private static String[] SPECIAL_WORDS = { "i18n", "uuid" };

	public String getID()
	{
		return getClass().getSimpleName();
	}

	public InputStream generate(DocumentationGenerationRequest req, MetaModelHolder holder, Set<DocumentationWarning> allWarnings, IPath actualOutputFile)
	{
		try
		{
			List<FieldMetaModel> fields = new ArrayList<FieldMetaModel>();
			for (TypeMetaModel typeMM : holder.getTypes())
			{
				if (MARKERS_HOLDER_CLASS.getCanonicalName().equals(typeMM.getName().getQualifiedName()))
				{
					for (IMemberMetaModel memberMM : typeMM.getMembers(holder))
					{
						if (memberMM instanceof FieldMetaModel)
						{
							FieldMetaModel fieldMM = (FieldMetaModel)memberMM;
							if (fieldMM.isStatic() && SERVOY_MARKER_CLASS.getSimpleName().equals(fieldMM.getType().getShortName()))
							{
								fields.add(fieldMM);
							}
						}
					}
				}
			}
			return generateContent(fields, holder);
		}
		catch (Throwable e)
		{
			LogUtil.logger().log(Level.SEVERE, "Exception while generating documentation.", e);
			return null;
		}
	}

	protected InputStream generateContent(List<FieldMetaModel> fields, MetaModelHolder holder) throws Exception
	{
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.newDocument();

		Element docRoot = doc.createElement("root");
		doc.appendChild(docRoot);

		Element markersEl = doc.createElement("markers");
		docRoot.appendChild(markersEl);

		for (FieldMetaModel fieldMM : fields)
		{
			Field f = MARKERS_HOLDER_CLASS.getField(fieldMM.getName());
			if (f != null)
			{
				ServoyMarker mk = (ServoyMarker)f.get(null);

				Element markerEl = doc.createElement("marker");
				markerEl.setAttribute("id", fieldMM.getName());
				markerEl.setAttribute("type", beautify(mk.getType()));
				markerEl.setAttribute("typecode", mk.getType());

				Element messageEl = doc.createElement("message");
				messageEl.appendChild(doc.createCDATASection(mk.getText()));
				markerEl.appendChild(messageEl);

				String description = "";
				JavadocMetaModel javadoc = fieldMM.getJavadoc(holder);
				if (javadoc != null)
				{
					description = ExtractorUtil.grabDescription(javadoc, fieldMM.getWarnings(), fieldMM.getFullSignature());
				}
				Element descrEl = doc.createElement("description");
				descrEl.appendChild(doc.createCDATASection(description));
				markerEl.appendChild(descrEl);

				if (mk.getFix() != null && mk.getFix().trim().length() > 0)
				{
					Element fixEl = doc.createElement("fix");
					fixEl.appendChild(doc.createCDATASection(mk.getFix()));
					markerEl.appendChild(fixEl);
				}

				markersEl.appendChild(markerEl);
			}
			else
			{
				LogUtil.logger().log(Level.SEVERE, "Cannot get field '" + fieldMM.getName() + "' via reflection.");
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

		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		return bais;
	}

	private static String beautify(String type)
	{
		StringBuffer sb = new StringBuffer();
		String lastPart = type;
		int idx = lastPart.lastIndexOf('.');
		if (idx >= 0) lastPart = lastPart.substring(idx + 1);
		lastPart = lastPart.substring(0, 1).toUpperCase() + lastPart.substring(1);
		Pattern p = Pattern.compile("[A-Z]+[a-z0-9]*");
		Matcher m = p.matcher(lastPart);
		while (m.find())
		{
			String word = m.group();
			boolean special = false;
			for (String s : SPECIAL_WORDS)
			{
				if (s.toLowerCase().equals(word.toLowerCase()))
				{
					special = true;
					break;
				}
			}
			if (special) word = word.toUpperCase();
			if (sb.length() > 0) sb.append(" ");
			sb.append(word);
		}
		return sb.toString();
	}

}
