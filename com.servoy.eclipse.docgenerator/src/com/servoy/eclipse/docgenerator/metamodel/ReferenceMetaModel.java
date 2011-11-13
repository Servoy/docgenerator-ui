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



/**
 * @author gerzse
 */
@SuppressWarnings("nls")
public class ReferenceMetaModel implements IJavadocPart
{
	public static enum QualifiedNameDisplayState
	{
		None, Simple, Full
	}

	private final String typeQualifiedName;
	private final String typeSimpleName;
	private final String memberName;
	private final String[] argumentsTypesNames;
	private final QualifiedNameDisplayState qnameState;

	public ReferenceMetaModel(String typeQualifiedName, String typeSimpleName, String memberName, String[] argumentsTypesNames,
		QualifiedNameDisplayState qnameState)
	{
		this.typeQualifiedName = typeQualifiedName;
		this.typeSimpleName = typeSimpleName;
		this.memberName = memberName;
		this.argumentsTypesNames = argumentsTypesNames;
		this.qnameState = qnameState;
	}

	// try to recover information from a raw String
	public static ReferenceMetaModel fromString(String allContent)
	{
		if (allContent == null)
		{
			return null;
		}
		boolean error = false;
		String typeName = null;
		String memberName = null;
		String[] argumentsTypes = null;
		QualifiedNameDisplayState qnameState = QualifiedNameDisplayState.None;
		String text = allContent.trim();
		String parts[] = text.split("#");
		String memberPart;
		if (parts.length > 1)
		{
			typeName = parts[0];
			memberPart = parts[1];
		}
		else
		{
			memberPart = text;
		}
		String argumentsPart;
		int leftParen = memberPart.indexOf('(');
		if (leftParen >= 0)
		{
			memberName = memberPart.substring(0, leftParen);
			argumentsPart = memberPart.substring(leftParen + 1);
			int rightParen = argumentsPart.indexOf(')');
			if (rightParen >= 0)
			{
				argumentsPart = argumentsPart.substring(0, rightParen);
				argumentsTypes = argumentsPart.split(",");
				for (int i = 0; i < argumentsTypes.length; i++)
				{
					argumentsTypes[i] = argumentsTypes[i].trim();
				}
			}
			else
			{
				error = true;
			}
		}
		else
		{
			memberName = memberPart;
		}
		if (!error)
		{
			String typeNameQualified = null;
			if (typeName != null)
			{
				int dotIdx = typeName.lastIndexOf('.');
				if (dotIdx >= 0)
				{
					typeNameQualified = typeName;
					typeName = typeNameQualified.substring(dotIdx + 1);
					qnameState = QualifiedNameDisplayState.Full;
				}
				else
				{
					qnameState = QualifiedNameDisplayState.Simple;
				}
			}
			return new ReferenceMetaModel(typeNameQualified, typeName, memberName, argumentsTypes, qnameState);
		}
		else
		{
			return null;
		}
	}

	public String getTypeQualifiedName()
	{
		return typeQualifiedName;
	}

	public String getTypeSimpleName()
	{
		return typeSimpleName;
	}

	public String getMemberName()
	{
		return memberName;
	}

	public String[] getArgumentsTypesNames()
	{
		return argumentsTypesNames;
	}

	@Override
	public String toString()
	{
		return toString(".", false);
	}

	public String getAsString(boolean clean)
	{
		return toString("#", true);
	}

	private String toString(String sep, boolean mandatorySep)
	{
		StringBuffer sb = new StringBuffer();
		if (qnameState != QualifiedNameDisplayState.None)
		{
			if (typeQualifiedName != null && qnameState == QualifiedNameDisplayState.Full)
			{
				sb.append(typeQualifiedName);
			}
			else if (typeSimpleName != null)
			{
				sb.append(typeSimpleName);
			}
		}
		if (mandatorySep || sb.length() > 0)
		{
			sb.append(sep);
		}
		if (memberName != null)
		{
			sb.append(memberName);
			if (argumentsTypesNames != null)
			{
				sb.append("(");
				for (int i = 0; i < argumentsTypesNames.length; i++)
				{
					if (i > 0) sb.append(",");
					sb.append(argumentsTypesNames[i]);
				}
				sb.append(")");
			}
		}
		return sb.toString();
	}
}
