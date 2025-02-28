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

import static com.servoy.eclipse.docgenerator.generators.DefaultDocumentationGenerator.STORE_KEY;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;

import com.servoy.eclipse.docgenerator.generators.MethodStoragePlace;

/**
 * Holds data for a method from a Java class. Compared to the information stored about a generic member, it adds:
 * - return type
 * - names and type of parameters
 * - varargs flag
 *
 * @author gerzse
 */
public class MethodMetaModel extends MemberMetaModel
{
	private final String indexSignature;
	private final String fullSignature;
	private final boolean varargs;
	private TypeName returnType;
	private LinkedHashMap<String, TypeName> parameters = new LinkedHashMap<>();

	private static final String ANNOTATION_JS_SIGNATURE = "JSSignature";

	public MethodMetaModel(String className, MethodDeclaration astNode)
	{
		super(className, astNode.getName().getFullyQualifiedName(), getVisibility(astNode), isStatic(astNode));

		Type ret = astNode.getReturnType2();
		if (ret != null)
		{
			returnType = new TypeName(ret, false, className + " - " + getName(), "method return", getWarnings());
		}

		boolean hasVarargs = false;
		for (Object parObj : astNode.parameters())
		{
			if (parObj instanceof SingleVariableDeclaration varDecl)
			{
				String parName = varDecl.getName().getFullyQualifiedName();
				Type type = varDecl.getType();
				TypeName parType = new TypeName(type, varDecl.isVarargs(), className + " - " + getName(), "method parameter", getWarnings());
				parameters.put(parName, parType);

				if (varDecl.isVarargs()) hasVarargs = true;
			}
		}
		varargs = hasVarargs;
		indexSignature = buildIndexSignature(name, parameters);
		fullSignature = buildFullSignature(returnType, className, name, parameters);
	}

	@Override
	public String getIndexSignature()
	{
		return indexSignature;
	}

	private MethodMetaModel(MethodMetaModel original, ITypeBinding typeArgument)
	{
		super(original.className, original.getName(), original.getVisibility(), original.isStatic());
		this.setJavadoc(original.getJavadoc(null));
		this.getWarnings().addAll(original.getWarnings());
		this.setAnnotations(original.getAnnotations());
		this.varargs = original.varargs;
		this.parameters.putAll(original.parameters);
		this.returnType = original.returnType.withType(typeArgument);
		indexSignature = buildIndexSignature(name, parameters);
		fullSignature = buildFullSignature(returnType, className, name, parameters);
		MethodStoragePlace methodData = (MethodStoragePlace)original.getStore().get(STORE_KEY);
		if (methodData != null)
			this.getStore().put(STORE_KEY, methodData.withMember(this));
		else
		{
			System.err.println("no Method data for " + original.getFullSignature());
		}
	}

	private static String buildIndexSignature(String name, Map<String, TypeName> parameters)
	{
		StringBuilder indexSig = new StringBuilder().append(name).append("(");
		boolean first = true;
		for (var parameter : parameters.entrySet())
		{
			if (!first)
			{
				indexSig.append(",");
			}
			indexSig.append(parameter.getValue().getQualifiedName());
			first = false;
		}
		return indexSig.append(")").toString();
	}

	private static String buildFullSignature(TypeName returnType, String className, String name, Map<String, TypeName> parameters)
	{
		StringBuilder fullSig = new StringBuilder();
		if (returnType != null)
		{
			fullSig.append(returnType.getQualifiedName()).append(" ");
		}
		fullSig.append(className).append(".").append(name).append("(");
		boolean first = true;
		for (var parameter : parameters.entrySet())
		{
			if (!first)
			{
				fullSig.append(", ");
			}
			fullSig.append(parameter.getValue().getShortName()).append(" ").append(parameter.getKey());
			first = false;
		}
		return fullSig.append(")").toString();
	}

	@Override
	public boolean matchesSignature(String signature)
	{
		int leftParen = signature.indexOf("(");
		if (leftParen >= 0)
		{
			String name = signature.substring(0, leftParen);
			if (getName().equals(name))
			{
				String args = signature.substring(leftParen + 1);
				if (args.endsWith(")"))
				{
					args = args.substring(0, args.length() - 1).trim();
					if (args.length() == 0 && parameters.size() == 0)
					{
						return true;
					}
					String[] parts = args.split(",");
					if (parts.length == parameters.size())
					{
						int i = 0;
						boolean good = true;
						for (TypeName tn : parameters.values())
						{
							String sigType = parts[i].trim();
							// for the types of the parameters accept either the qualified name or the simple name
							if (!sigType.equals(tn.getShortName()) && !sigType.equals(tn.getQualifiedName()))
							{
								good = false;
								break;
							}
							i++;
						}
						if (good)
						{
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	@Override
	public String getFullSignature()
	{
		return fullSignature;
	}

	@Override
	public TypeName getType()
	{
		return returnType;
	}

	public boolean isVarargs()
	{
		return varargs;
	}

	public LinkedHashMap<String, TypeName> getParameters()
	{
		return parameters;
	}

	@Override
	public void setAnnotations(AnnotationsList ann)
	{
		super.setAnnotations(ann);
		AnnotationMetaModel jsSignature = ann.getAnnotation(ANNOTATION_JS_SIGNATURE);
		if (jsSignature != null)
		{
			Object arguments = jsSignature.getAttribute("arguments");
			if (arguments instanceof Object[])
			{
				Object[] array = (Object[])arguments;
				int index = 0;
				LinkedHashMap<String, TypeName> newParams = new LinkedHashMap<String, TypeName>();
				for (Entry<String, TypeName> entry : parameters.entrySet())
				{
					ITypeBinding tb = (ITypeBinding)array[index++];
					TypeName value = new TypeName(tb, entry.getValue().isVarargs());
					newParams.put(entry.getKey(), value);
				}
				this.parameters = newParams;
			}
			Object returns = jsSignature.getAttribute("returns");
			if (returns instanceof ITypeBinding && !((ITypeBinding)returns).getBinaryName().equals("java.lang.Object"))
			{
				returnType = new TypeName((ITypeBinding)returns, false);
			}
		}
	}

	@Override
	public ClientSupport getServoyClientSupport(TypeMetaModel tmm, MetaModelHolder holder)
	{
		return ClientSupport.fromAnnotation(holder.getAnnotationManager().getAnnotation(this, tmm, ANNOTATION_SERVOY_CLIENT_SUPPORT));
	}

	@Override
	public IMemberMetaModel withType(ITypeBinding typeArgument)
	{
		return new MethodMetaModel(this, typeArgument);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((className == null) ? 0 : className.hashCode());
		result = prime * result + ((indexSignature == null) ? 0 : indexSignature.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		MethodMetaModel other = (MethodMetaModel)obj;
		if (className == null)
		{
			if (other.className != null) return false;
		}
		else if (!className.equals(other.className)) return false;
		if (indexSignature == null)
		{
			if (other.indexSignature != null) return false;
		}
		else if (!indexSignature.equals(other.indexSignature)) return false;
		return true;
	}
}
