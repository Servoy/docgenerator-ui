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

package com.servoy.eclipse.docgenerator.client;

import com.servoy.eclipse.docgenerator.generators.DefaultDocumentationGenerator;
import com.servoy.eclipse.docgenerator.generators.IStoragePlaceFactory;
import com.servoy.eclipse.docgenerator.generators.MemberKindIndex;
import com.servoy.eclipse.docgenerator.generators.MemberStoragePlace;
import com.servoy.eclipse.docgenerator.generators.TypeMapper;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;

/**
 * @author gerzse
 */
public class DesigntimeDocumentationGenerator extends DefaultDocumentationGenerator
{
	public static final String TAG_EVENT = "event";
	private static final String TAG_EVENTS = "events";
	public static final String TAG_COMMAND = "command";
	private static final String TAG_COMMANDS = "commands";

	public static final String COMMAND_SUFFIX = "CmdMethodID";
	public static final String EVENT_SUFFIX = "MethodID";
	public static final String PROPERTY_SUFFIX = "ID";

	/**
	 * In design time we add two types of members: events and commands.
	 */
	private static class DesigntimeMemberKindIndex extends MemberKindIndex
	{
		protected DesigntimeMemberKindIndex()
		{
			kindsToTags.put(TAG_EVENT, TAG_EVENTS);
			kindsToTags.put(TAG_COMMAND, TAG_COMMANDS);
		}
	}

	@Override
	public String getID()
	{
		return getClass().getSimpleName();
	}

	@Override
	protected IStoragePlaceFactory getDataFactory(TypeMapper fp)
	{
		return new DesigntimeMemberDataFactory(fp);
	}

	@Override
	protected MemberKindIndex getMemberKindIndex()
	{
		return new DesigntimeMemberKindIndex();
	}

	@Override
	protected boolean includeSample()
	{
		return false;
	}

	@Override
	protected void recomputeForAll(MetaModelHolder holder)
	{
		for (TypeMetaModel typeMM : holder.getTypes())
		{
			for (IMemberMetaModel memberMM : typeMM.getMembers(holder))
			{
				MemberStoragePlace memberData = (MemberStoragePlace)memberMM.getStore().get(STORE_KEY);
				if (memberData instanceof DesigntimeMethodStoragePlace)
				{
					DesigntimeMethodStoragePlace designtimeMethodData = (DesigntimeMethodStoragePlace)memberData;
					designtimeMethodData.recompute(typeMM);
				}
			}
		}
	}
}
