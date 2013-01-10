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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;

/**
 * Class that holds relevant information for a member of a class. 
 * Relevant from the point of view of the default documentation generator.
 * Other documentation generators may find other information relevant.
 * 
 * @author gerzse
 */
@SuppressWarnings("nls")
public abstract class MemberStoragePlace
{
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
	private static final String ATTR_SIMPLIFIEDSIGNATURE = "simplifiedSignature";
	private static final String ATTR_STATICCALL = "staticCall";
	protected static final String ATTR_NAME = "name";
	private static final String TAG_RETURN = "return";
	protected static final String ATTR_SERVOY_MOBILE = DefaultDocumentationGenerator.ATTR_SERVOY_MOBILE;

	private DocumentationDataDistilled docData;
	private TypeName type;
	private boolean deprecated;
	protected final TypeMetaModel typeMM;
	protected final IMemberMetaModel memberMM;

	public MemberStoragePlace(IMemberMetaModel memberMM, TypeMetaModel typeMM)
	{
		this.memberMM = memberMM;
		this.typeMM = typeMM;
		type = memberMM.getType();
		deprecated = memberMM.isDeprecated();

		if (memberMM.getJavadoc() != null)
		{
			docData = new DocumentationDataDistilled(memberMM, typeMM);
		}
	}

	public TypeName getType()
	{
		return type;
	}

	/**
	 * The type may change due to type mappings.
	 */
	public void setType(TypeName type)
	{
		this.type = type;
	}

	public DocumentationDataDistilled getDocData()
	{
		return docData;
	}

	/**
	 * The documentation data may change due to @sameas, @clonedesc, @sampleas tags
	 * and due to inheritance (in case of undocumented members).
	 */
	public void setDocData(DocumentationDataDistilled docData)
	{
		this.docData = docData;
	}

	public boolean isDeprecated()
	{
		return deprecated;
	}

	/**
	 * The deprecated flag may change.
	 * In particular this happens in the custom documentation generator which documents
	 * design-time elements.
	 */
	public void setDeprecated(boolean deprecated)
	{
		this.deprecated = deprecated;
	}

	abstract public String getKind();

	public String getOfficialName()
	{
		return memberMM.getName();
	}

	public String getOfficialSignature()
	{
		return memberMM.getIndexSignature();
	}

	public boolean hasServoyMobileAnnotation(MetaModelHolder holder)
	{
		if (memberMM instanceof MemberMetaModel)
		{
			return ((MemberMetaModel)memberMM).hasServoyMobileAnnotation(typeMM, holder);
		}
		return false;
	}

	protected Element toXML(Document domDoc, boolean includeSample, MetaModelHolder holder, boolean docMobile)
	{
		Element root = domDoc.createElement(getKind());
		root.setAttribute(ATTR_NAME, getOfficialName());
		if (isDeprecated())
		{
			root.setAttribute(DefaultDocumentationGenerator.ATTR_DEPRECATED, Boolean.TRUE.toString());
		}
		DocumentationDataDistilled ddr = getDocData();
		if (!hideReturnType() && getType() != null)
		{
			Element retType = domDoc.createElement(TAG_RETURN);
			retType.setAttribute(DefaultDocumentationGenerator.ATTR_TYPE, getType().getQualifiedName());
			retType.setAttribute(DefaultDocumentationGenerator.ATTR_TYPECODE, getType().getBinaryName());
			if (ddr != null && ddr.getReturn() != null && ddr.getReturn().trim().length() > 0)
			{
				retType.appendChild(domDoc.createCDATASection(ddr.getReturn().trim()));
			}
			root.appendChild(retType);
		}
		if (docMobile && hasServoyMobileAnnotation(holder))
		{
			root.setAttribute(ATTR_SERVOY_MOBILE, Boolean.TRUE.toString());
		}
		if (ddr != null && ddr.hasDocumentation())
		{
			if (ddr.isSpecial())
			{
				root.setAttribute(ATTR_SPECIAL, Boolean.TRUE.toString());
			}
			if (ddr.isSimplifiedSignature())
			{
				root.setAttribute(ATTR_SIMPLIFIEDSIGNATURE, Boolean.TRUE.toString());
			}
			if (ddr.isStaticCall())
			{
				root.setAttribute(ATTR_STATICCALL, Boolean.TRUE.toString());
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
			if (includeSample)
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

	public void mapTypes(MetaModelHolder holder, TypeMapper proc)
	{
		TypeName newTN = proc.mapType(holder, getType(), false, new boolean[1]);
		setType(newTN);
	}

	abstract public boolean shouldShow(TypeMetaModel realTypeMM);

	protected boolean hideReturnType()
	{
		return false;
	}

}
