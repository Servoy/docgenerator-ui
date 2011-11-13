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

import com.servoy.eclipse.docgenerator.metamodel.ReferenceMetaModel;

/**
 * Used for storing references from one class member to another. Such references appear at the
 * @sameas, @sampleas, @clonedesc, @see tags.
 * 
 * @author gerzse
 */
@SuppressWarnings("nls")
public class QualifiedNameRaw
{
	/**
	 * The name of the referenced class.
	 */
	private String className;

	/**
	 * The name of the referenced member. If a method is referenced, then only the name is stored here.
	 */
	private final String memberName;

	/**
	 * The signature of the referenced member. For method this includes the types of the arguments.
	 */
	private final String memberSignature;

	public QualifiedNameRaw(String className, String memberSignature, String memberName)
	{
		this.className = className;
		this.memberSignature = memberSignature;
		String adjustedMemberName = memberName;
		if (adjustedMemberName == null && memberSignature != null)
		{
			int idx = memberSignature.indexOf("(");
			if (idx >= 0) adjustedMemberName = memberSignature.substring(0, idx);
			else adjustedMemberName = memberSignature;
		}
		this.memberName = adjustedMemberName;
	}

	public QualifiedNameRaw(ReferenceMetaModel ref)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(ref.getMemberName());
		if (ref.getArgumentsTypesNames() != null)
		{
			sb.append("(");
			for (int i = 0; i < ref.getArgumentsTypesNames().length; i++)
			{
				if (i > 0) sb.append(",");
				sb.append(ref.getArgumentsTypesNames()[i]);
			}
			sb.append(")");
		}
		this.className = ref.getTypeQualifiedName();
		this.memberSignature = sb.toString();
		this.memberName = ref.getMemberName();
	}

	public void setEnclosingType(String qname)
	{
		if (className == null)
		{
			className = qname;
		}
	}

	public String getClassName()
	{
		return className;
	}

	public String getMemberSignature()
	{
		return memberSignature;
	}

	public String getMemberName()
	{
		return memberName;
	}

	@Override
	public String toString()
	{
		return className + "#" + memberSignature;
	}
}
