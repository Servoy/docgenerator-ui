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

import java.util.StringTokenizer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;

/**
 * Holds information extracted from a @param tag from Javadocs.
 * 
 * @author gerzse
 */
public class DocumentedParameterData
{
	private static final String TAG_PARAMETER = "parameter";
	private static final String ATTR_NAME = "name";
	private static final String ATTR_OPTIONAL = "optional";
	private static final String ATTR_DESCRIPTION = "description";

	private final String name;
	private final boolean optional;
	private String description;
	private TypeName type;
	private boolean typeWasChecked = false;

	public DocumentedParameterData(String name, boolean optional, String description)
	{
		super();
		this.name = name;
		this.optional = optional;
		this.description = description;
	}

	public String getName()
	{
		return name;
	}

	public boolean isOptional()
	{
		return optional;
	}

	public String getDescription()
	{
		return description;
	}

	public TypeName getType()
	{
		return type;
	}

	public void checkIfHasType(MetaModelHolder holder, TypeMapper proc)
	{
		if (!typeWasChecked)
		{
			if (description != null)
			{
				StringTokenizer strtok = new StringTokenizer(description);
				if (strtok.hasMoreTokens())
				{
					String first = strtok.nextToken();
					TypeName tn = new TypeName(first, null);
					boolean[] flag = new boolean[1];
					TypeName tentativeType = proc.mapType(holder, tn, false, flag);
					if (flag[0])
					{
						type = tentativeType;
						description = description.substring(first.length()).trim();
					}
				}
			}
			typeWasChecked = true;
		}
	}

	public void checkIfHasType(MetaModelHolder holder, TypeMapper proc, String typeString)
	{
		if (!typeWasChecked)
		{
			TypeName tn = new TypeName(typeString, null);
			boolean[] flag = new boolean[1];
			TypeName tentativeType = proc.mapType(holder, tn, true, flag);
			if (flag[0])
			{
				type = tentativeType;
			}
			typeWasChecked = true;
		}
	}

	public Element toXML(Document domDoc)
	{
		Element root = domDoc.createElement(TAG_PARAMETER);
		root.setAttribute(ATTR_NAME, name);
		if (optional)
		{
			root.setAttribute(ATTR_OPTIONAL, Boolean.TRUE.toString());
		}
		if (description != null && description.trim().length() > 0)
		{
			Element elDescr = domDoc.createElement(ATTR_DESCRIPTION);
			elDescr.appendChild(domDoc.createCDATASection(description));
			root.appendChild(elDescr);
		}
		return root;
	}
}
