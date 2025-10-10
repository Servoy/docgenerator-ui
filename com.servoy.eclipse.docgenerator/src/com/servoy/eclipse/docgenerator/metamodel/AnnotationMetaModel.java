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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author gerzse
 */
public class AnnotationMetaModel
{
	private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();
	private final String name;

	public AnnotationMetaModel(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void addAttribute(String nm, Object attribute)
	{
		attributes.put(nm, attribute);
	}

	public boolean hasAttribute(String nm)
	{
		return attributes.containsKey(nm);
	}

	public <T> T getAttribute(String nm)
	{
		return (T)attributes.get(nm);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append('@').append(name);
		if (attributes.size() > 0)
		{
			sb.append('(');
			boolean first = true;
			for (Entry<String, Object> entry : attributes.entrySet())
			{
				if (!first)
				{
					sb.append(',');
				}
				sb.append(entry.getKey()).append('=');
				Object value = entry.getValue();
				if (value.getClass().isArray())
				{
					Object[] arr = (Object[])value;
					sb.append('{');
					for (int i = 0; i < arr.length; i++)
					{
						if (i > 0) sb.append(',');
						sb.append(arr[i].toString());
					}
					sb.append('}');
				}
				else if (value instanceof String)
				{
					sb.append('"');
					sb.append(value.toString());
					sb.append('"');
				}
				else
				{
					sb.append(value.toString());
				}
				first = false;
			}
			sb.append(')');
		}
		return sb.toString();
	}
}
