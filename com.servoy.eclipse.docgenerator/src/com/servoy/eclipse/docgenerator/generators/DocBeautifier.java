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
import com.servoy.eclipse.docgenerator.metamodel.GenericMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel.Visibility;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;
import com.servoy.j2db.dataprocessing.DataException;
import com.servoy.j2db.dataprocessing.FoundSet;
import com.servoy.j2db.dataprocessing.Record;
import com.servoy.j2db.dataprocessing.RelatedFoundSet;
import com.servoy.j2db.scripting.JSUnitAssertFunctions;
import com.servoy.j2db.ui.IScriptRenderMethods;
import com.servoy.j2db.util.ServoyException;

/**
 * Helper class that does some post-processing on the generated documentation XMLs.
 * Specifically it performs some cleanup on the documentation for the Servoy internal classes.
 * 
 * @author gerzse
 */
public class DocBeautifier
{
	private static class FakeConstantMetaModel extends GenericMemberMetaModel
	{
		private final String name;
		private final String fullSignature;
		private final TypeName type;

		public FakeConstantMetaModel(String className, String name, Class< ? > retType)
		{
			this.name = name;

			StringBuffer sb = new StringBuffer();
			sb.append(retType.getCanonicalName()).append(" ").append(className).append(".").append(getName());
			fullSignature = sb.toString();

			type = new TypeName(retType);
		}

		private FakeConstantMetaModel(FakeConstantMetaModel original)
		{
			this.name = original.name;
			this.fullSignature = original.fullSignature;
			this.type = original.type;
		}

		public String getName()
		{
			return name;
		}

		public TypeName getType()
		{
			return type;
		}

		public JavadocMetaModel getJavadoc()
		{
			return null;
		}

		public JavadocMetaModel getJavadoc(MetaModelHolder holder)
		{
			return null;
		}

		public boolean isDeprecated()
		{
			return false;
		}

		public String getIndexSignature()
		{
			return name;
		}

		public String getFullSignature()
		{
			return fullSignature;
		}

		public boolean matchesSignature(String signature)
		{
			return name.equals(signature);
		}

		public Visibility getVisibility()
		{
			return Visibility.Public;
		}

		public boolean isStatic()
		{
			return true;
		}

		public IMemberMetaModel duplicate()
		{
			return new FakeConstantMetaModel(this);
		}

		public boolean isDuplicate()
		{
			return false;
		}
	}

	private final MetaModelHolder holder;

	public DocBeautifier(MetaModelHolder holder)
	{
		this.holder = holder;
	}

	public void beautify()
	{
		// remove from DataException all constants that are also present in ServoyException
		String servoyExName = ServoyException.class.getCanonicalName();
		TypeMetaModel servoyEx = holder.getType(servoyExName);
		for (String dbExName : new String[] { DataException.class.getCanonicalName() })
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
		Set<String> names = new HashSet<String>();
		names.add(FoundSet.class.getCanonicalName());
		names.add(Record.class.getCanonicalName());
		names.add(JSUnitAssertFunctions.class.getCanonicalName());
		names.add(RelatedFoundSet.class.getCanonicalName());
		names.add(IScriptRenderMethods.class.getCanonicalName());
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
