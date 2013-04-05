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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.TagElement;

import com.servoy.eclipse.docgenerator.metamodel.ClientSupport;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocTagPart;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.util.Pair;

/**
 * Holds information extracted from one Javadoc:
 * - description
 * - sample (normal and mobilesample) code
 * - list of @param tags
 * - @return tag
 * - list of @link tags
 * - @since tag
 * - @until tag
 * - @deprecated tag
 * 
 * If any of the @sameas, @sampleas or @clonedesc tags appear inside the Javadoc, they are also recorded.
 * 
 * @author gerzse
 */
@SuppressWarnings("nls")
public class DocumentationDataDistilled
{
	public static final String TAG_MOBILEDESCRIPTION = "@mobiledescription";
	public static final String TAG_SAMPLE = "@sample";
	public static final String TAG_MOBILESAMPLE = "@mobilesample";
	public static final String TAG_SAMEAS = "@sameas";
	public static final String TAG_SAMPLE_AS = "@sampleas";
	public static final String TAG_CLONEDESC = "@clonedesc";
	public static final String TAG_UNTIL = "@until";
	public static final String TAG_SPECIAL = "@special";
	public static final String TAG_SIMPLIFIEDSIGNATURE = "@simplifiedSignature";
	public static final String TAG_STATICCALL = "@staticCall";

	public static final String FLAG_OPTIONAL = "optional"; //$NON-NLS-1$

	private final List<Pair<ClientSupport, String>> texts;
	private final List<Pair<ClientSupport, String>> summaries;
	private final List<Pair<String, String>> samples = new ArrayList<Pair<String, String>>();
	private final List<DocumentedParameterData> parameters = new ArrayList<DocumentedParameterData>();
	private String ret;
	private final List<String> links = new ArrayList<String>();
	private String since;
	private String until;
	private QualifiedNameRaw sameAs;
	private QualifiedNameRaw cloneSample;
	private QualifiedNameRaw cloneDescription;
	private boolean special = false;
	private boolean simplifiedSignature = false;
	private boolean staticCall = false;
	private String deprecatedText;

	public DocumentationDataDistilled(IMemberMetaModel memberMM, TypeMetaModel typeMM, JavadocMetaModel jdoc)
	{
		Set<DocumentationWarning> warnings = memberMM.getWarnings();
		String location = memberMM.getFullSignature();

		boolean clean = true;

		texts = new ArrayList<Pair<ClientSupport, String>>();
		summaries = new ArrayList<Pair<ClientSupport, String>>();

		String descriptionText = ExtractorUtil.grabExactlyOne(JavadocMetaModel.TEXT_TAG, clean, jdoc, warnings, location);
		String mobileDescriptionText = ExtractorUtil.grabExactlyOne(TAG_MOBILEDESCRIPTION, clean, jdoc, warnings, location);
		List<Pair<ClientSupport, String>> txts = new ArrayList<Pair<ClientSupport, String>>();
		txts.add(new Pair<ClientSupport, String>(ClientSupport.Default, descriptionText));
		if (mobileDescriptionText != null && mobileDescriptionText.trim().length() > 0) txts.add(new Pair<ClientSupport, String>(ClientSupport.mc,
			mobileDescriptionText.trim()));
		setTexts(txts);

		addSampleCodeWithTag(TAG_SAMPLE, ExtractorUtil.grabExactlyOne(TAG_SAMPLE, clean, jdoc, warnings, location));
		addSampleCodeWithTag(TAG_MOBILESAMPLE, ExtractorUtil.grabExactlyOne(TAG_MOBILESAMPLE, clean, jdoc, warnings, location));

		deprecatedText = ExtractorUtil.grabExactlyOne(TagElement.TAG_DEPRECATED, clean, jdoc, warnings, location);
		ret = ExtractorUtil.grabExactlyOne(TagElement.TAG_RETURN, clean, jdoc, warnings, location);
		since = ExtractorUtil.grabExactlyOne(TagElement.TAG_SINCE, clean, jdoc, warnings, location);
		until = ExtractorUtil.grabExactlyOne(TAG_UNTIL, clean, jdoc, warnings, location);
		sameAs = ExtractorUtil.grabReference(TAG_SAMEAS, jdoc, warnings, location);
		if (sameAs != null)
		{
			sameAs.setEnclosingType(typeMM.getName().getQualifiedName());
		}
		cloneSample = ExtractorUtil.grabReference(TAG_SAMPLE_AS, jdoc, warnings, location);
		if (cloneSample != null)
		{
			cloneSample.setEnclosingType(typeMM.getName().getQualifiedName());
		}
		cloneDescription = ExtractorUtil.grabReference(TAG_CLONEDESC, jdoc, warnings, location);
		if (cloneDescription != null)
		{
			cloneDescription.setEnclosingType(typeMM.getName().getQualifiedName());
		}

		JavadocTagPart specialTag = ExtractorUtil.grabFirstTag(TAG_SPECIAL, jdoc, true, warnings, location);
		if (specialTag != null)
		{
			special = true;
		}

		JavadocTagPart simplifiedSignatureTag = ExtractorUtil.grabFirstTag(TAG_SIMPLIFIEDSIGNATURE, jdoc, true, warnings, location);
		if (simplifiedSignatureTag != null)
		{
			simplifiedSignature = true;
		}

		JavadocTagPart staticCallTag = ExtractorUtil.grabFirstTag(TAG_STATICCALL, jdoc, true, warnings, location);
		if (staticCallTag != null)
		{
			staticCall = true;
		}

		List<JavadocTagPart> paramTags = jdoc.findTags(TagElement.TAG_PARAM);
		for (JavadocTagPart paramTag : paramTags)
		{
			String paramText = paramTag.getAsString(clean);
			paramText = paramText.trim();

			StringTokenizer st = new StringTokenizer(paramText);
			if (st.hasMoreTokens())
			{
				String paramName = st.nextToken();
				boolean isOptional = false;
				String paramDescription = null;
				if (st.hasMoreTokens())
				{
					String maybeOptional = st.nextToken();
					if (maybeOptional.equals(FLAG_OPTIONAL))
					{
						isOptional = true;
						int idx = paramText.indexOf(FLAG_OPTIONAL);
						paramDescription = paramText.substring(idx + FLAG_OPTIONAL.length()).trim();
					}
					else
					{
						paramDescription = paramText.substring(paramName.length()).trim();
					}
				}
				if (paramDescription == null)
				{
					paramDescription = "";
					warnings.add(new DocumentationWarning(WarningType.ParamTagWithoutContent, location, TagElement.TAG_PARAM + " tag without text: '" +
						paramName + "'."));
				}
				DocumentedParameterData parData = new DocumentedParameterData(paramName, isOptional, paramDescription);
				parameters.add(parData);
			}
			else
			{
				warnings.add(new DocumentationWarning(WarningType.EmptyTag, location, "Empty " + TagElement.TAG_PARAM + " tag."));
			}
		}

		List<JavadocTagPart> linkTags = jdoc.findTags(TagElement.TAG_LINK);
		for (JavadocTagPart linkTag : linkTags)
		{
			String linkText = linkTag.getAsString(clean).trim();
			int idx = linkText.indexOf(' ');
			if (idx >= 0)
			{
				linkText = linkText.substring(0, idx);
			}
			links.add(linkText);
		}
	}

	private void addSampleCodeWithTag(String sampleTagType, String sample)
	{
		if (sample != null)
		{
			boolean found = false;
			for (Pair<String, String> smpl : samples)
			{
				if (smpl.getRight().equals(sample))
				{
					found = true;
					// add back the "*/"
					smpl.setRight(Pattern.compile("\\*&#47;").matcher(sample).replaceAll("*/"));
					break;
				}
			}

			if (!found)
			{
				samples.add(new Pair<String, String>(sampleTagType, sample));
			}
		}
	}

	public boolean hasDocumentation()
	{
		if (texts.size() > 0) return true;
		if (samples.size() > 0) return true;
		if (sameAs != null) return true;
		if (cloneSample != null) return true;
		if (cloneDescription != null) return true;
		if (links.size() > 0) return true;
		if (deprecatedText != null && deprecatedText.trim().length() > 0) return true;
		if (since != null && since.trim().length() > 0) return true;
		if (until != null && until.trim().length() > 0) return true;
		return false;
	}

	public List<Pair<ClientSupport, String>> getTexts()
	{
		return texts;
	}

	public List<Pair<ClientSupport, String>> getSummaries()
	{
		return summaries;
	}

	public void setTexts(List<Pair<ClientSupport, String>> texts)
	{
		this.texts.clear();
		this.texts.addAll(texts);

		for (Pair<ClientSupport, String> text : texts)
		{
			if (text.getRight() != null)
			{
				//also set summary here
				String txt = text.getRight();
				String summary = "";
				int idx = txt.indexOf('.');
				if (idx >= 0) summary = txt.substring(0, idx + 1);
				else summary = txt;
				summaries.add(new Pair<ClientSupport, String>(text.getLeft(), summary));
			}
		}
	}

	public String getSummary(ClientSupport csp)
	{
		for (Pair<ClientSupport, String> summary : summaries)
		{
			if (summary.getLeft().supports(csp)) return summary.getRight();
		}
		return null;
	}

	public void setSummaries(List<Pair<ClientSupport, String>> summaries)
	{
		this.summaries.clear();
		this.summaries.addAll(summaries);
	}

	public List<Pair<String, String>> getSamples()
	{
		return samples;
	}

	public void setSamples(List<Pair<String, String>> samples)
	{
		this.samples.addAll(samples);
	}

	public void addParameter(DocumentedParameterData param)
	{
		parameters.add(param);
	}

	public List<DocumentedParameterData> getParameters()
	{
		return parameters;
	}

	public String getReturn()
	{
		return ret;
	}

	public void setReturn(String ret)
	{
		this.ret = ret;
	}

	public QualifiedNameRaw getSameAs()
	{
		return sameAs;
	}

	public void setSameAs(QualifiedNameRaw sameAs)
	{
		this.sameAs = sameAs;
	}

	public QualifiedNameRaw getCloneSample()
	{
		return cloneSample;
	}

	public void setCloneSample(QualifiedNameRaw cloneSample)
	{
		this.cloneSample = cloneSample;
	}

	public QualifiedNameRaw getCloneDescription()
	{
		return cloneDescription;
	}

	public void setCloneDescription(QualifiedNameRaw cloneDescription)
	{
		this.cloneDescription = cloneDescription;
	}

	public String getDeprecatedText()
	{
		return deprecatedText;
	}

	public void setDeprecatedText(String text)
	{
		this.deprecatedText = text;
	}

	public String getSince()
	{
		return since;
	}

	public void setSince(String since)
	{
		this.since = since;
	}

	public String getUntil()
	{
		return until;
	}

	public void setUntil(String until)
	{
		this.until = until;
	}

	public boolean isSpecial()
	{
		return special;
	}

	public void setSpecial(boolean special)
	{
		this.special = special;
	}

	public List<String> getLinks()
	{
		return links;
	}

	public boolean isSimplifiedSignature()
	{
		return simplifiedSignature;
	}

	public void setSimplifiedSignature(boolean simplifiedSignature)
	{
		this.simplifiedSignature = simplifiedSignature;
	}

	/**
	 * @return the staticCall
	 */
	public boolean isStaticCall()
	{
		return staticCall;
	}

	/**
	 * @param staticCall the staticCall to set
	 */
	public void setStaticCall(boolean staticCall)
	{
		this.staticCall = staticCall;
	}
}
