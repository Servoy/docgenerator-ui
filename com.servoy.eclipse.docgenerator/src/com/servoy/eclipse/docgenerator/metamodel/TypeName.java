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

import java.util.Set;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Type;

import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;

/**
 * Holds various ways of referring to a certain Java class:
 * - qualified name
 * - short name
 * - binary name
 *
 * This is needed, for example, when outputting the data to XML, where
 * both the qualified names and the binary names of referenced classed
 * are needed.
 *
 * @author gerzse
 */
public class TypeName
{
	private static final ITypeBinding[] NO_TYPE_ARGUMENTS = new ITypeBinding[0];

	/**
	 * Holds the qualified name of the base form of this type (no array or generics information).
	 * This is needed for identifying the same type even if appears as arrays with
	 * different dimensions for example.
	 *
	 * @see adaptQualifiedName(String)
	 * @see buildQualifiedName()
	 */
	private final String baseQualifiedName;

	/**
	 * The fully qualified name of the class, including array information, if any.
	 *
	 * @see adaptQualifiedName(String)
	 * @see buildQualifiedName()
	 */
	private final String qualifiedName;

	/**
	 * Holds the short name of the class, without package information, but with array
	 * information, if any. This is needed because we map for example from the java.lang.String
	 * class to a fake String class that is used only for documentation purposes.
	 *
	 * @see buildShortName()
	 */
	private final String shortName;

	/**
	 * Holds the binary name of the base form of this type (no array information, no generics).
	 * This is needed when building the binary name for array types.
	 *
	 * @see buildBinaryName()
	 */
	private final String baseBinaryName;

	/**
	 * The binary name of the class. This can be used to load the class with Class.forName(...)
	 * for example.
	 *
	 * @see buildBinaryName()
	 */
	private final String binaryName;

	/**
	 * Number of dimensions in case of array types. It is 0 if the type is not an array type.
	 */
	private final int dimensions;

	/**
	 * Flag that tells us if we are dealing with a primitive type. This is needed when building the
	 * binary representation of the class name (it is built differently for primitive types).
	 *
	 * @see buildBinaryName()
	 */
	private final boolean primitive;

	/**
	 * The level of nesting, when the class is defined inside other classes. This is needed because
	 * for nested classes the '$' is used as a delimiter between the name of the class and the names
	 * of the container classes.
	 */
	private final int nestingLevel;

	private final boolean varargs;

	private final ITypeBinding[] typeArguments;

	public TypeName(ITypeBinding binding, boolean varargs)
	{
		this.varargs = varargs;
		ITypeBinding inner = binding;
		if (varargs)
		{
			dimensions = 1;
		}
		else
		{
			int dims = 0;
			while (inner.isArray())
			{
				inner = inner.getComponentType();
				dims += 1;
			}
			dimensions = dims;
		}
		if (inner.isParameterizedType())
		{
			typeArguments = inner.getTypeArguments();
		}
		else
		{
			typeArguments = NO_TYPE_ARGUMENTS;
		}
		ITypeBinding parent = inner;
		int nesting = 0;
		while (parent.isNested())
		{
			nesting++;
			parent = parent.getDeclaringClass();
		}
		nestingLevel = nesting;
		primitive = inner.isPrimitive();
		baseQualifiedName = adaptQualifiedName(inner.getQualifiedName());
		baseBinaryName = ifnull(inner.getBinaryName(), baseQualifiedName);
		shortName = buildShortName(baseBinaryName, dimensions);
		qualifiedName = buildQualifiedName(baseQualifiedName, dimensions);
		binaryName = buildBinaryName(baseBinaryName, baseQualifiedName, dimensions, primitive);
	}

	/**
	 * Create an instance based on a JDT type.
	 *
	 * The location, context and warnings parameters are used for raising warnings when the type bindings
	 * cannot be resolved.
	 */
	public TypeName(Type type, boolean varargs, String location, String context, Set<DocumentationWarning> warnings)
	{
		this.varargs = varargs;
		// Try to resolve the binding.
		ITypeBinding binding = type.resolveBinding();
		// If binding was resolved, then use it to extract the names.
		if (binding != null)
		{
			ITypeBinding inner = binding;
			if (varargs)
			{
				dimensions = 1;
			}
			else
			{
				int dims = 0;
				while (inner.isArray())
				{
					inner = inner.getComponentType();
					dims += 1;
				}
				dimensions = dims;
			}

			if (inner.isParameterizedType())
			{
				typeArguments = inner.getTypeArguments();
			}
			else
			{
				typeArguments = NO_TYPE_ARGUMENTS;
			}

			ITypeBinding parent = inner;
			int nesting = 0;
			while (parent.isNested())
			{
				nesting++;
				parent = parent.getDeclaringClass();
			}
			nestingLevel = nesting;
			primitive = inner.isPrimitive();
			baseQualifiedName = adaptQualifiedName(inner.getQualifiedName());
			baseBinaryName = ifnull(inner.getBinaryName(), baseQualifiedName);
		}
		// If the binding was not resolved, then use the type name anyway.
		// This may not be accurate, but is better than nothing.
		// Also raise a warning in this case.
		else
		{
			typeArguments = NO_TYPE_ARGUMENTS;
			String rawName = type.toString();
			int idx = rawName.indexOf("[");
			if (idx >= 0)
			{
				baseQualifiedName = rawName.substring(0, idx);
				String dimPart = rawName.substring(idx);
				dimensions = dimPart.length() / 2;
			}
			else
			{
				baseQualifiedName = rawName;
				dimensions = 0;
			}
			nestingLevel = 0;
			baseBinaryName = baseQualifiedName;
			primitive = type.isPrimitiveType();
			DocumentationWarning dw = new DocumentationWarning(WarningType.UnresolvedBinding, location, "Cannot resolve binding for " + context + " type: '" +
				baseQualifiedName + "'.");
			warnings.add(dw);
		}
		shortName = buildShortName(baseBinaryName, dimensions);
		qualifiedName = buildQualifiedName(baseQualifiedName, dimensions);
		binaryName = buildBinaryName(baseBinaryName, baseQualifiedName, dimensions, primitive);
	}

	/**
	 * Create an instance based on a String qualified name and a possible container name
	 * (package, maybe container class).
	 */
	public TypeName(String qName, String containerName)
	{
		dimensions = 0;
		varargs = false;
		typeArguments = NO_TYPE_ARGUMENTS;
		int nesting = 0;
		StringBuffer sb = new StringBuffer();
		if (containerName != null)
		{
			sb.append(containerName);
			for (int i = 0; i < sb.length(); i++)
			{
				if (sb.charAt(i) == '$')
				{
					nesting++;
				}
			}
		}
		sb.append(qName);
		nestingLevel = nesting;
		baseQualifiedName = sb.toString();
		baseBinaryName = baseQualifiedName;
		primitive = false;
		shortName = buildShortName(baseBinaryName, dimensions);
		qualifiedName = buildQualifiedName(baseQualifiedName, dimensions);
		binaryName = buildBinaryName(baseBinaryName, baseQualifiedName, dimensions, primitive);
	}

	/**
	 * Create an instance based on a Class.
	 */
	public TypeName(Class< ? > cls)
	{
		int dim = 0;
		Class< ? > inner = cls;
		while (inner.isArray())
		{
			inner = cls.getComponentType();
			dim += 1;
		}
		dimensions = dim;
		Class< ? > parent = cls;
		int nesting = 0;
		while (parent.isMemberClass())
		{
			nesting++;
			parent = parent.getDeclaringClass();
		}
		nestingLevel = nesting;
		typeArguments = NO_TYPE_ARGUMENTS;
		baseQualifiedName = adaptQualifiedName(cls.getCanonicalName());
		baseBinaryName = cls.getName();
		primitive = cls.isPrimitive();
		varargs = false;
		shortName = buildShortName(baseBinaryName, dimensions);
		qualifiedName = buildQualifiedName(baseQualifiedName, dimensions);
		binaryName = buildBinaryName(baseBinaryName, baseQualifiedName, dimensions, primitive);
	}

	/**
	 * Construct a new instance based on an existing instance, by changing only the binary name and
	 * the array information.
	 */
	private TypeName(TypeName source, String baseBinaryName, String binaryName, int newDimensions)
	{
		this.baseQualifiedName = source.baseQualifiedName;
		this.nestingLevel = source.nestingLevel;
		this.baseBinaryName = baseBinaryName;
		this.binaryName = binaryName;
		this.varargs = source.varargs;
		typeArguments = NO_TYPE_ARGUMENTS;
		dimensions = newDimensions;
		primitive = source.primitive;
		shortName = buildShortName(baseBinaryName, dimensions);
		qualifiedName = buildQualifiedName(baseQualifiedName, dimensions);
	}

	private TypeName(TypeName original, ITypeBinding typeArgument)
	{
		this.nestingLevel = original.nestingLevel;
		this.varargs = original.varargs;
		this.primitive = original.primitive;
		this.dimensions = original.dimensions;

		typeArguments = NO_TYPE_ARGUMENTS;

		baseQualifiedName = adaptQualifiedName(typeArgument.getQualifiedName());
		baseBinaryName = typeArgument.getBinaryName();
		shortName = buildShortName(baseBinaryName, dimensions);
		qualifiedName = buildQualifiedName(baseQualifiedName, dimensions);
		binaryName = buildBinaryName(baseBinaryName, baseQualifiedName, dimensions, primitive);
	}

	/**
	 * @return the varargs
	 */
	public boolean isVarargs()
	{
		return varargs;
	}

	public String getShortName()
	{
		return shortName;
	}

	public String getBaseBinaryName()
	{
		return baseBinaryName;
	}

	public String getBaseName()
	{
		return baseQualifiedName;
	}

	public String getQualifiedName()
	{
		return qualifiedName;
	}

	public String getBinaryName()
	{
		return binaryName;
	}

	public ITypeBinding[] getTypeArguments()
	{
		return typeArguments;
	}

	/**
	 * Two types are the same if their base qualified names are the same.
	 */
	public boolean isSameType(TypeName other)
	{
		if (other != null)
		{
			return baseQualifiedName.equals(other.baseQualifiedName);
		}
		return false;
	}

	/**
	 * Change the qualified and short names, but keep the binary names.
	 * The name of the class will look different, but still the original class
	 * will get loaded based on the original binary name.
	 */
	public TypeName changeBaseTo(TypeName src)
	{
		return new TypeName(src, baseBinaryName, binaryName, dimensions);
	}

	public TypeName withType(ITypeBinding typeArgument)
	{
		return new TypeName(this, typeArgument);
	}

	/**
	 * Build the base qualified name, starting from the raw qualified name and
	 * given a nesting level. Basically replaces the last '.' separators with '$'.
	 */
	private String adaptQualifiedName(String rawQualifiedName)
	{
		StringBuffer sb = new StringBuffer(rawQualifiedName);
		int cnt = nestingLevel;
		int idx = -1;
		do
		{
			idx = sb.lastIndexOf(".");
			if (idx >= 0 && cnt > 0)
			{
				sb.setCharAt(idx, '$');
				cnt--;
			}
		}
		while (idx >= 0 && cnt > 0);
		return sb.toString();
	}

	/**
	 * Build the qualified name of the class, starting from the base qualified name.
	 * Basically just add the needed "[]" for arrays.
	 */
	private static String buildQualifiedName(String baseQualifiedName, int dimensions)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(baseQualifiedName);
		for (int i = 0; i < dimensions; i++)
			sb.append("[]");
		return sb.toString();
	}

	/**
	 * Builds the short name of this class. Basically takes the last segment from the
	 * base binary name and adds the required "[]"s for arrays.
	 */
	private static String buildShortName(String baseBinaryName, int dimensions)
	{
		StringBuilder sb = new StringBuilder();
		int idxDot = baseBinaryName.lastIndexOf(".");
		int idxDollar = baseBinaryName.lastIndexOf("$");
		int idx = Math.max(idxDot, idxDollar);
		if (idx >= 0)
		{
			sb.append(baseBinaryName.substring(idx + 1));
		}
		else
		{
			sb.append(baseBinaryName);
		}
		for (int i = 0; i < dimensions; i++)
			sb.append("[]");
		return sb.toString();
	}

	/**
	 * Builds the binary name of the class. For array types the binary name has a specific form.
	 * For non-array types the binary name is the same as the qualified name.
	 */
	private static String buildBinaryName(String baseBinaryName, String baseQualifiedName, int dimensions, boolean primitive)
	{
		StringBuffer sb = new StringBuffer();
		if (dimensions > 0)
		{
			for (int i = 0; i < dimensions; i++)
			{
				sb.append("[");
			}
			if (!primitive)
			{
				sb.append("L");
			}
			sb.append(baseBinaryName);
			if (!primitive)
			{
				sb.append(";");
			}
		}
		else
		{
			sb.append(baseQualifiedName);
		}
		return sb.toString();
	}

	private static String ifnull(String string1, String string2)
	{
		if (string1 == null)
		{
			return string2;
		}
		return string1;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((qualifiedName == null) ? 0 : qualifiedName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		TypeName other = (TypeName)obj;
		if (qualifiedName == null)
		{
			if (other.qualifiedName != null) return false;
		}
		else if (!qualifiedName.equals(other.qualifiedName)) return false;
		return true;
	}

	@Override
	public String toString()
	{
		return "TypeName[" + getQualifiedName() + ':' + getBinaryName() + ']';
	}
}
