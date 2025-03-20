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

import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Holds information about a field from a Java class. Compared to what is stored about a generic member of a class,
 * this adds:
 * - type
 * - static flag
 * - public flag
 *
 * @author gerzse
 */
public class FieldMetaModel extends MemberMetaModel
{
	private final TypeName type;
	private final String indexSignature;
	private final String fullSignature;

	public FieldMetaModel(String className, BodyDeclaration fld, String name)
	{
		super(className, name, getVisibility(fld), isStatic(fld));

		if (fld instanceof FieldDeclaration fieldDeclaration)
		{
			type = new TypeName(fieldDeclaration.getType(), false, getClassName() + " - " + getName(), "field type", getWarnings());
		}
		else if (fld instanceof EnumConstantDeclaration enumConstantDeclaration)
		{
			type = new TypeName(enumConstantDeclaration.resolveVariable().getType(), false);
		}
		else
		{
			throw new RuntimeException("FieldMetaModel can only be created for FieldDeclaration or EnumConstantDeclaration");
		}

		indexSignature = getName();

		StringBuffer sb = new StringBuffer();
		sb.append(getType().getQualifiedName()).append(" ").append(className).append(".").append(getName());
		fullSignature = sb.toString();
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

	@Override
	public ClientSupport getServoyClientSupport(TypeMetaModel tmm, MetaModelHolder holder)
	{
		return ClientSupport.fromAnnotation(holder.getAnnotationManager().getAnnotation(this, ANNOTATION_SERVOY_CLIENT_SUPPORT));
	}

	@Override
	public IMemberMetaModel withType(ITypeBinding iTypeBinding)
	{
		throw new RuntimeException("NOT IMPLEMENTED (yet)");
	}
}
