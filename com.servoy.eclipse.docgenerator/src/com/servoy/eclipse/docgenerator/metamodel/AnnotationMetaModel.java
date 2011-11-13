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

/**
 * @author gerzse
 */
@SuppressWarnings("nls")
public class AnnotationMetaModel extends LinkedHashMap<String, Object>
{
	private final String name;

	public AnnotationMetaModel(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("@").append(name);
		if (size() > 0)
		{
			sb.append("(");
			boolean first = true;
			for (String key : keySet())
			{
				if (!first)
				{
					sb.append(",");
				}
				sb.append(key).append("=");
				Object value = get(key);
				if (value.getClass().isArray())
				{
					Object[] arr = (Object[])value;
					sb.append("{");
					for (int i = 0; i < arr.length; i++)
					{
						if (i > 0) sb.append(",");
						sb.append(arr[i].toString());
					}
					sb.append("}");
				}
				else if (value instanceof String)
				{
					sb.append("\"");
					sb.append(value.toString());
					sb.append("\"");
				}
				else
				{
					sb.append(value.toString());
				}
				first = false;
			}
			sb.append(")");
		}
		return sb.toString();
	}
}
