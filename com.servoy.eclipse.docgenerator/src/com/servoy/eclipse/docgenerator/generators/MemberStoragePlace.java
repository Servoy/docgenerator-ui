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

import com.servoy.eclipse.docgenerator.metamodel.ClientSupport;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;
import com.servoy.eclipse.docgenerator.util.Pair;

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
	private static final String TAG_SAMPLES = "samples";
	private static final String TAG_SUMMARIES = "summaries";
	private static final String TAG_SUMMARY = "summary";
	private static final String TAG_DESCRIPTIONS = "descriptions";
	private static final String TAG_DESCRIPTION = "description";
	private static final String ATTR_SINCE = "since";
	private static final String ATTR_UNTIL = "until";
	private static final String ATTR_SPECIAL = "special";
	private static final String ATTR_SIMPLIFIEDSIGNATURE = "simplifiedSignature";
	private static final String ATTR_STATICCALL = "staticCall";
	protected static final String ATTR_NAME = "name";
	private static final String TAG_RETURN = "return";

	private DocumentationDataDistilled docData;
	private TypeName type;
	private boolean deprecated;
	protected final TypeMetaModel typeMM;
	protected final IMemberMetaModel memberMM;
	protected final MetaModelHolder holder;

	public MemberStoragePlace(IMemberMetaModel memberMM, TypeMetaModel typeMM, MetaModelHolder holder)
	{
		this.memberMM = memberMM;
		this.typeMM = typeMM;
		this.holder = holder;
		type = memberMM.getType();
		deprecated = memberMM.isDeprecated();

		JavadocMetaModel jdoc = memberMM.getJavadoc(holder);
		if (jdoc != null)
		{
			docData = new DocumentationDataDistilled(memberMM, typeMM, jdoc, holder);
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

	public DocumentationDataDistilled getDocDataRecursively(TypeMetaModel tmm)
	{
		if (docData != null)
		{
			return docData;
		}

		for (TypeName intfName : tmm.getInterfaces())
		{
			TypeMetaModel intf = holder.getType(intfName);
			if (intf != null)
			{
				IMemberMetaModel member = intf.getMember(memberMM.getIndexSignature(), null);
				if (member != null)
				{
					MemberStoragePlace data = (MemberStoragePlace)member.getStore().get(DefaultDocumentationGenerator.STORE_KEY);
					if (data != null && data.getDocData() != null)
					{
						return data.getDocData();
					}
				}
			}
		}

		if (tmm.getSupertype() == null)
		{
			return null;
		}

		TypeMetaModel sup = holder.getType(tmm.getSupertype());
		if (sup == null)
		{
			return null;
		}

		IMemberMetaModel member = sup.getMember(memberMM.getIndexSignature(), null);
		if (member != null)
		{
			MemberStoragePlace data = (MemberStoragePlace)member.getStore().get(DefaultDocumentationGenerator.STORE_KEY);
			if (data != null && data.getDocData() != null)
			{
				return data.getDocData();
			}
		}

		return getDocDataRecursively(sup);
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

	public ClientSupport getServoyClientSupport(TypeMetaModel tmm)
	{
		if (memberMM instanceof MemberMetaModel)
		{
			return ((MemberMetaModel)memberMM).getServoyClientSupport(tmm, holder);
		}
		return null;
	}

	protected Element toXML(TypeMetaModel tmm, Document domDoc, boolean includeSample, ClientSupport scp)
	{
		Element root = domDoc.createElement(getKind());
		root.setAttribute(ATTR_NAME, getOfficialName());
		if (isDeprecated())
		{
			root.setAttribute(DefaultDocumentationGenerator.ATTR_DEPRECATED, Boolean.TRUE.toString());
		}
		DocumentationDataDistilled ddr = getDocDataRecursively(tmm);
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
		if (scp != null)
		{
			root.setAttribute(DefaultDocumentationGenerator.ATTR_CLIENT_SUPPORT, scp.toAttribute());
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
			if (ddr.getTexts() != null && ddr.getTexts().size() > 0)
			{
				Element descriptionsEl = domDoc.createElement(TAG_DESCRIPTIONS);
				Element summariesEl = domDoc.createElement(TAG_SUMMARIES);
				for (Pair<ClientSupport, String> text : ddr.getTexts())
				{
					if (text != null && text.getRight() != null)
					{
						ClientSupport cs = (ddr.getSamples().size() > 1 ? text.getLeft() : scp);

						Element descr = domDoc.createElement(TAG_DESCRIPTION);
						descr.setAttribute(DefaultDocumentationGenerator.ATTR_CLIENT_SUPPORT, cs.toAttribute());
						descr.appendChild(domDoc.createCDATASection(text.getRight().trim()));
						descriptionsEl.appendChild(descr);

						String summary = ddr.getSummary(text.getLeft());
						if (summary != null && summary.trim().length() > 0)
						{
							Element summaryEl = domDoc.createElement(TAG_SUMMARY);
							summaryEl.setAttribute(DefaultDocumentationGenerator.ATTR_CLIENT_SUPPORT, cs.toAttribute());
							summaryEl.appendChild(domDoc.createCDATASection(summary.trim()));
							summariesEl.appendChild(summaryEl);
						}
					}
				}
				root.appendChild(descriptionsEl);
				root.appendChild(summariesEl);
			}
			if (ddr.getDeprecatedText() != null && ddr.getDeprecatedText().trim().length() > 0)
			{
				Element deprecatedElement = domDoc.createElement(TAG_DEPRECATED);
				root.appendChild(deprecatedElement);
				deprecatedElement.appendChild(domDoc.createCDATASection(ddr.getDeprecatedText().trim()));
			}
			if (includeSample && ddr.getSamples() != null && ddr.getSamples().size() > 0)
			{
				Element samples = domDoc.createElement(TAG_SAMPLES);
				for (Pair<ClientSupport, String> sample : ddr.getSamples())
				{
					if (sample != null && sample.getLeft() != null)
					{
						ClientSupport cs = (ddr.getSamples().size() > 1 ? sample.getLeft() : scp);

						Element sampleElem = domDoc.createElement(TAG_SAMPLE);
						sampleElem.setAttribute(DefaultDocumentationGenerator.ATTR_CLIENT_SUPPORT, cs.toAttribute());
						sampleElem.appendChild(domDoc.createCDATASection(sample.getRight().trim()));
						samples.appendChild(sampleElem);
					}
				}
				root.appendChild(samples);
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

	public void mapTypes(TypeMapper proc)
	{
		setType(proc.mapType(holder, getType(), false, new boolean[1]));
	}

	abstract public Pair<Boolean, ClientSupport> shouldShow(TypeMetaModel realTypeMM);

	protected boolean hideReturnType()
	{
		return false;
	}
}
