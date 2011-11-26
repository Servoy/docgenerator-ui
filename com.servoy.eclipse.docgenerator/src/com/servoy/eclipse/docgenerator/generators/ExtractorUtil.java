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

import java.util.List;
import java.util.Set;

import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.IJavadocPart;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocTagPart;
import com.servoy.eclipse.docgenerator.metamodel.ReferenceMetaModel;

/**
 * @author gerzse
 */
public class ExtractorUtil
{

	public static String grabExactlyOne(String tagName, boolean clean, JavadocMetaModel jdoc, Set<DocumentationWarning> warnings, String location)
	{
		String result = null;
		JavadocTagPart tag = grabFirstTag(tagName, jdoc, false, warnings, location);
		if (tag != null)
		{
			result = tag.getAsString(clean);
			result = result.trim();
		}
		return result;
	}

	public static QualifiedNameRaw grabReference(String tagName, JavadocMetaModel jdoc, Set<DocumentationWarning> warnings, String location)
	{
		ReferenceMetaModel result = null;
		JavadocTagPart tag = grabFirstTag(tagName, jdoc, false, warnings, location);
		if (tag != null)
		{
			for (IJavadocPart part : tag.getParts())
			{
				if (part instanceof ReferenceMetaModel)
				{
					if (result == null)
					{
						result = (ReferenceMetaModel)part;
					}
					else
					{
						warnings.add(new DocumentationWarning(WarningType.TooMuchContentForTag, location, tagName +
							" has too much content. Only a reference was expected: " + tag.getAsString(false)));
					}
				}
			}
			// try to interpret raw string content
			if (result == null)
			{
				String allContent = tag.getAsString(true);
				result = ReferenceMetaModel.fromString(allContent);
				if (result == null)
				{
					warnings.add(new DocumentationWarning(WarningType.UnresolvedBinding, location, tagName +
						" does not contain a valid reference towards a Java field or method: '" + allContent + "'."));
				}
			}
		}
		if (result != null)
		{
			return new QualifiedNameRaw(result);
		}
		else
		{
			return null;
		}
	}

	public static String grabDescription(JavadocMetaModel jdoc, Set<DocumentationWarning> warnings, String location)
	{
		String result = null;
		JavadocTagPart tag = grabFirstTag(JavadocMetaModel.TEXT_TAG, jdoc, false, warnings, location);
		if (tag != null)
		{
			result = tag.getAsString(true);
			result = result.trim();
		}
		return result;
	}

	public static JavadocTagPart grabFirstTag(String tagName, JavadocMetaModel jdoc, boolean mayBeEmpty, Set<DocumentationWarning> warnings, String location)
	{
		JavadocTagPart result = null;
		List<JavadocTagPart> tags = jdoc.findTags(tagName);
		if (tags.size() > 0)
		{
			result = tags.get(0);
			if (!mayBeEmpty && result.getAsString(false).trim().length() == 0)
			{
				warnings.add(new DocumentationWarning(WarningType.EmptyTag, location, tagName + " is empty."));
			}
			if (tags.size() > 1)
			{
				warnings.add(new DocumentationWarning(WarningType.MultipleTags, location, "More than one " + tagName +
					" tags encountered. Only the first one is processed."));
			}
		}
		return result;
	}

}
