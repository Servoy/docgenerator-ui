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

package com.servoy.eclipse.docgenerator.metamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author gerzse
 */
public class JavadocTagPart implements IJavadocPart, IJavadocPartsHolder
{
	private final String tagName;
	private final List<IJavadocPart> parts = new ArrayList<IJavadocPart>();

	public JavadocTagPart(String tagName)
	{
		this.tagName = tagName;
	}

	public String getName()
	{
		return tagName;
	}

	public List<IJavadocPart> getParts()
	{
		return Collections.unmodifiableList(parts);
	}

	public void addPart(IJavadocPart part)
	{
		parts.add(part);
	}

	public List<JavadocTagPart> findTags(String requestedTagName)
	{
		List<JavadocTagPart> tags = new ArrayList<JavadocTagPart>();
		for (IJavadocPart part : parts)
		{
			if (part instanceof JavadocTagPart)
			{
				JavadocTagPart tag = (JavadocTagPart)part;
				if (requestedTagName.equals(tag.getName()))
				{
					tags.add(tag);
				}
			}
		}
		return Collections.unmodifiableList(tags);
	}

	public void compress()
	{
		IJavadocPart previous = null;
		List<IJavadocPart> newParts = new ArrayList<IJavadocPart>();
		for (IJavadocPart part : parts)
		{
			if (part instanceof JavadocTagPart)
			{
				((JavadocTagPart)part).compress();
			}
			if (previous != null)
			{
				if (previous instanceof JavadocTextPart && part instanceof JavadocTextPart)
				{
					previous = new JavadocTextPart(((JavadocTextPart)previous).getContent() + ((JavadocTextPart)part).getContent());
				}
				else
				{
					newParts.add(previous);
					previous = part;
				}
			}
			else
			{
				previous = part;
			}
		}
		if (previous != null)
		{
			newParts.add(previous);
		}
		parts.clear();
		parts.addAll(newParts);
	}

	public String getAsString(boolean clean)
	{
		StringBuffer sb = new StringBuffer();
		gather(sb, clean, 0);
		return sb.toString();
	}

	private void gather(StringBuffer sb, boolean clean, int level)
	{
		int entryLen = sb.length();
		if (!clean && level > 1)
		{
			sb.append("{");
			sb.append(tagName);
		}
		for (IJavadocPart child : parts)
		{
			if (child instanceof JavadocTagPart)
			{
				((JavadocTagPart)child).gather(sb, clean, level + 1);
			}
			else
			{
				sb.append(child.getAsString(clean));
			}
		}
		if (!clean && level > 1)
		{
			sb.append("}");
		}
		if (clean)
		{
			if (sb.length() > entryLen && sb.charAt(entryLen) == ' ')
			{
				sb.delete(entryLen, entryLen + 1);
			}
		}
	}

}
