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

/**
 * Holds one warning about something in the documentation. There are several categories
 * of warnings, to make it easier to review them.
 * 
 * @author gerzse
 */
@SuppressWarnings("nls")
public class DocumentationWarning implements Comparable<DocumentationWarning>
{
	public static enum WarningType
	{
		EmptyTag, MissingTag, MultipleTags, ParamTagWithoutContent, TooMuchContentForTag, UnresolvedBinding, RedirectionProblem, Other
	}

	private final WarningType type;
	private final String location;
	private final String message;

	public DocumentationWarning(WarningType type, String location, String message)
	{
		this.type = type;
		this.location = location != null ? location.trim() : "";
		this.message = message.trim();
	}

	public WarningType getType()
	{
		return type;
	}

	public String getLocation()
	{
		return location;
	}

	public String getMessage()
	{
		return message;
	}

	public int compareTo(DocumentationWarning o)
	{
		int cmpType = this.type.toString().compareTo(o.type.toString());
		if (cmpType == 0)
		{
			int cmpLoc = this.location.compareTo(o.location);
			if (cmpLoc == 0)
			{
				return this.message.compareTo(o.message);
			}
			else
			{
				return cmpLoc;
			}
		}
		else
		{
			return cmpType;
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (o instanceof DocumentationWarning)
		{
			DocumentationWarning dw = (DocumentationWarning)o;
			return this.compareTo(dw) == 0;
		}
		return false;
	}

	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(type.toString());
		if (location.length() > 0) sb.append(" - ").append(location);
		sb.append(": ").append(message);
		return sb.toString();
	}

}
