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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.servoy.eclipse.docgenerator.metamodel.FieldMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;

/**
 * Helper class that does some post-processing on the generated documentation XMLs.
 * Specifically it performs some cleanup on the documentation for the Servoy internal classes.
 *
 * @author gerzse
 */

public class DocBeautifier
{
	private final MetaModelHolder holder;

	public DocBeautifier(MetaModelHolder holder)
	{
		this.holder = holder;
	}

	public void beautify()
	{
		// remove from DataException all constants that are also present in ServoyException
		String servoyExName = "com.servoy.j2db.util.ServoyException";
		TypeMetaModel servoyEx = holder.getType(servoyExName);
		for (String dbExName : new String[] { "com.servoy.j2db.dataprocessing.DataException" })
		{
			TypeMetaModel dbEx = holder.getType(dbExName);
			if (servoyEx != null && dbEx != null)
			{
				List<String> toRemove = new ArrayList<String>();
				for (IMemberMetaModel memb : dbEx.getMembers())
				{
					if (memb instanceof FieldMetaModel && servoyEx.getMember(memb.getIndexSignature(), holder) != null)
					{
						toRemove.add(memb.getIndexSignature());
					}
				}
				for (String s : toRemove)
				{
					dbEx.removeMember(s);
				}
			}
		}
		// Remove all constants from some of the classes.
		// TODO these should be kept in sync....
		Set<String> names = new HashSet<String>();
		names.add("com.servoy.j2db.dataprocessing.FoundSet");
		names.add("com.servoy.j2db.dataprocessing.Record");
		names.add("com.servoy.j2db.scripting.JSUnitAssertFunctions");
		names.add("com.servoy.j2db.dataprocessing.RelatedFoundSet");
		names.add("com.servoy.j2db.ui.IScriptRenderMethods");
		for (String name : names)
		{
			TypeMetaModel cdr = holder.getType(name);
			if (cdr != null)
			{
				List<String> toRemove = new ArrayList<String>();
				for (IMemberMetaModel memb : cdr.getMembers())
				{
					if (memb instanceof FieldMetaModel)
					{
						toRemove.add(memb.getIndexSignature());
					}
				}
				for (String s : toRemove)
				{
					cdr.removeMember(s);
				}
			}
		}
	}
}
