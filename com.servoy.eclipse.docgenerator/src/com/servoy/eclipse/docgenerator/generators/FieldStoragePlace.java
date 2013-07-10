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
import java.util.List;

import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel.Visibility;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;

/**
 * @author gerzse
 */
public class FieldStoragePlace extends MemberStoragePlace
{
	public FieldStoragePlace(IMemberMetaModel memberMM, TypeMetaModel typeMM, MetaModelHolder holder)
	{
		super(memberMM, typeMM, holder);
	}

	@Override
	public String getKind()
	{
		return DefaultDocumentationGenerator.TAG_CONSTANT;
	}

	@Override
	public boolean shouldShow(TypeMetaModel realTypeMM)
	{
		boolean constantProvider = false;
		final List<TypeMetaModel> ancestors = new ArrayList<TypeMetaModel>();
		for (TypeMetaModel tmm : allAncestors(realTypeMM, ancestors))
		{
			if (tmm != null && tmm.isInterface() && tmm.getName() != null && tmm.getName().getQualifiedName() != null &&
				tmm.getName().getQualifiedName().contains("IConstantsObject"))
			{
				constantProvider = true;
				break;
			}
		}
		return constantProvider && memberMM.getVisibility() == Visibility.Public && memberMM.isStatic();
	}

	private List<TypeMetaModel> allAncestors(TypeMetaModel tmm, List<TypeMetaModel> ancestors)
	{
		if (tmm != null)
		{
			TypeName parent = tmm.getSupertype();
			if (parent != null)
			{
				ancestors.add(holder.getType(parent));
				allAncestors(holder.getType(parent), ancestors);
			}

			for (TypeName interf : tmm.getInterfaces())
			{
				ancestors.add(holder.getType(interf));
				allAncestors(holder.getType(interf), ancestors);
			}
		}
		return ancestors;
	}
}
