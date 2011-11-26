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

import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Holds information about a field from a Java class. Compared to what is stored about a generic member of a class,
 * this adds:
 * - type
 * - static flag
 * - public flag
 * 
 * @author gerzse
 */
@SuppressWarnings("nls")
public class FieldMetaModel extends MemberMetaModel
{
	private final TypeName type;
	private final String indexSignature;
	private final String fullSignature;

	public FieldMetaModel(String className, FieldDeclaration fld, VariableDeclarationFragment astNode)
	{
		super(className, astNode.getName().getFullyQualifiedName(), fld);

		Type t = fld.getType();
		type = new TypeName(t, false, getClassName() + " - " + getName(), "field type", getWarnings());

		indexSignature = getName();

		StringBuffer sb = new StringBuffer();
		sb.append(getType().getQualifiedName()).append(" ").append(className).append(".").append(getName());
		fullSignature = sb.toString();
	}

	private FieldMetaModel(FieldMetaModel original)
	{
		super(original);
		this.type = original.type;
		this.indexSignature = original.indexSignature;
		this.fullSignature = original.indexSignature;
	}

	@Override
	public TypeName getType()
	{
		return type;
	}

	@Override
	public String getFullSignature()
	{
		return fullSignature;
	}

	@Override
	public String getIndexSignature()
	{
		return indexSignature;
	}

	@Override
	public boolean matchesSignature(String signature)
	{
		return indexSignature.equals(signature);
	}

	public FieldMetaModel duplicate()
	{
		return new FieldMetaModel(this);
	}
}
