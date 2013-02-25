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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * Holds an index of all parsed Java classes, indexed by their qualified name.
 * Also handled the generation of the documentation XML file.
 * 
 * @author gerzse
 */
public class MetaModelHolder
{
	private final Map<String, TypeMetaModel> types = new TreeMap<String, TypeMetaModel>();

	// In the XML the classes used to be written in alphabetic order of their public name,
	// so we also keep them in a sorted set to respect this order.
	private final SortedSet<TypeMetaModel> sortedTypes = new TreeSet<TypeMetaModel>();

	public void addType(String key, TypeMetaModel value)
	{
		types.put(key, value);
		sortedTypes.add(value);
	}

	public SortedSet<TypeMetaModel> getSortedTypes()
	{
		return sortedTypes;
	}

	/**
	 * Performs any final steps needed to ensure that the registry is consistent.
	 */
	public void finalTouch()
	{
		for (TypeMetaModel typeMM : types.values())
		{
			copyMissingMembersFromAbove(typeMM, typeMM);
		}
	}

	/**
	 * For the "target" class, copies recursively from super class and interfaces
	 * any missing member.
	 */
	private void copyMissingMembersFromAbove(TypeMetaModel current, TypeMetaModel target)
	{
		List<TypeMetaModel> sources = new ArrayList<TypeMetaModel>();
		if (current.getSupertype() != null)
		{
			TypeMetaModel sup = getType(current.getSupertype().getQualifiedName());
			if (sup != null)
			{
				copyMissingMembersFromAbove(sup, sup);
				sources.add(sup);
			}
		}
		for (TypeName interf : current.getInterfaces())
		{
			TypeMetaModel src = getType(interf.getQualifiedName());
			if (src != null)
			{
				copyMissingMembersFromAbove(src, src);
				sources.add(src);
			}
		}
		for (TypeMetaModel typeMM : sources)
		{
			for (IMemberMetaModel memberMM : typeMM.getMembers())
			{
				if (!target.hasMember(memberMM.getIndexSignature()))
				{
					target.addMember(memberMM.getIndexSignature(), memberMM.duplicate());
				}
			}
		}
		for (TypeMetaModel src : sources)
		{
			copyMissingMembersFromAbove(src, target);
		}
	}

	public TypeMetaModel getType(String name)
	{
		return types.get(name);
	}

	public boolean hasType(String name)
	{
		return types.containsKey(name);
	}

	public Collection<TypeMetaModel> getTypes()
	{
		return types.values();
	}

}
