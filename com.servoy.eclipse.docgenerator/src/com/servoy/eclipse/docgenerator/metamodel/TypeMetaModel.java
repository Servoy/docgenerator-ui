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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;


/**
 * Holds class level information about Javadocs:
 * - qualified name, public name, scripting name
 * - deprecated flag
 * - if the class should appear or not in the generated XML
 * - supertype
 * - implemented interfaces
 *
 * Note that all Java classes are parsed for Javadocs, even if they should not appear
 * in the generated XML. This is because of the @sameas, @sampleas and @clonedesc tags,
 * which may refer to any Java method, from any class.
 *
 * @author gerzse
 */
public class TypeMetaModel implements Comparable<TypeMetaModel>, IPublicStore
{
	private final boolean isInterface;
	private final List<TypeParameter> typeParameters;

	Map<String, IMemberMetaModel> members = new TreeMap<String, IMemberMetaModel>();

	/**
	 * Default category to use when the "category" attribute is not explicitly given in the @ServoyDocumented annotation.
	 *
	 */
	private static final String DEFAULT_CATEGORY = "plugins";

	private static final String ATTR_PUBLIC_NAME = "publicName";
	private static final String ATTR_SCRIPTING_NAME = "scriptingName";
	private static final String ATTR_CATEGORY_NAME = "category";
	private static final String ATTR_EXTENDS_COMPONENT = "extendsComponent";
	private static final String ATTRIBUTE_IS_BUTTON = "isButton";
	private static final String ATTRIBUTE_DISPLAY_TYPE = "displayType";
	private static final String ATTRIBUTE_REAL_CLASS = "realClass";

	private final TypeName name;

	private TypeName supertypeName;
	private final List<TypeName> interfaceNames = new ArrayList<TypeName>();

	private JavadocMetaModel jd;
	private AnnotationsList ann;

	private final Map<String, Object> store = new HashMap<String, Object>();

	/**
	 * Set of warnings raised while processing information about the class (type bindings, etc.).
	 *
	 * Note that each member has a separate set of warnings, distinct from those of the class.
	 * This is so because at the end only the relevant warnings will be written to a file (the
	 * warnings that correspond to those members that will appear in the generated XML).
	 */
	private final Set<DocumentationWarning> warnings = new TreeSet<DocumentationWarning>();

	public TypeMetaModel(String packageName, List<String> ancestorClassNames, TypeDeclaration astNode, boolean isInterface)
	{
		this.isInterface = isInterface;
		this.typeParameters = astNode.typeParameters();
		String parentName = packageName + ".";
		for (String s : ancestorClassNames)
		{
			parentName += s + "$";
		}
		name = new TypeName(astNode.getName().getFullyQualifiedName(), parentName);

		if (astNode.getSuperclassType() != null)
		{
			supertypeName = new TypeName(astNode.getSuperclassType(), false, name.getQualifiedName(), "supertype", warnings);
		}
		List< ? > superInterfaces = astNode.superInterfaceTypes();
		if (superInterfaces != null)
		{
			for (Object o : superInterfaces)
			{
				if (o instanceof Type)
				{
					interfaceNames.add(new TypeName((Type)o, false, name.getQualifiedName(), "interface", warnings));
				}
			}
		}
	}

	public TypeMetaModel(String packageName, List<String> ancestorClassNames, EnumDeclaration astNode)
	{
		this.isInterface = false;
		this.typeParameters = new ArrayList<TypeParameter>();
		String parentName = packageName + ".";
		for (String s : ancestorClassNames)
		{
			parentName += s + "$";
		}
		name = new TypeName(astNode.getName().getFullyQualifiedName(), parentName);

		List< ? > superInterfaces = astNode.superInterfaceTypes();
		if (superInterfaces != null)
		{
			for (Object o : superInterfaces)
			{
				if (o instanceof Type)
				{
					interfaceNames.add(new TypeName((Type)o, false, name.getQualifiedName(), "interface", warnings));
				}
			}
		}
	}

	public Map<String, Object> getStore()
	{
		return store;
	}

	public TypeName getName()
	{
		return name;
	}

	public String getPublicName()
	{
		return getStringAttribute(ATTR_PUBLIC_NAME, getName().getShortName());
	}

	public String getScriptingName()
	{
		return getStringAttribute(ATTR_SCRIPTING_NAME, null);
	}

	public String getCategory()
	{
		return getStringAttribute(ATTR_CATEGORY_NAME, DEFAULT_CATEGORY);
	}

	public boolean isDeprecated()
	{
		return ann.hasAnnotation(IPublicStore.ANNOTATION_DEPRECATED);
	}

	public String getExtendsComponent()
	{
		return getStringAttribute(ATTR_EXTENDS_COMPONENT, null);
	}

	public boolean isButton()
	{
		return Boolean.TRUE.equals(getAttribute(ATTRIBUTE_IS_BUTTON));
	}

	public int getDisplayType()
	{
		return getIntAttribute(ATTRIBUTE_DISPLAY_TYPE, -1);
	}

	public String getRealClassName()
	{
		ITypeBinding val = (ITypeBinding)getAttribute(ATTRIBUTE_REAL_CLASS);
		if (val != null)
		{
			String className = val.getBinaryName();
			if (className != null && !"java.lang.Object".equals(className))
			{
				return className;
			}
		}
		return null;
	}

	private String getStringAttribute(String key, String def)
	{
		Object val = getAttribute(key);
		if (val != null)
		{
			String result = val.toString();
			if (result.trim().length() > 0)
			{
				return result;
			}
		}

		return def;
	}

	private int getIntAttribute(String key, int def)
	{
		Object val = getAttribute(key);
		if (val instanceof Integer)
		{
			return ((Integer)val).intValue();
		}

		return def;
	}

	private Object getAttribute(String key)
	{
		AnnotationMetaModel amm = ann.getAnnotation(ANNOTATION_SERVOY_DOCUMENTED);
		if (amm != null && amm.hasAttribute(key))
		{
			return amm.getAttribute(key);
		}
		return null;
	}

	public JavadocMetaModel getJavadoc()
	{
		return jd;
	}

	public void setJavadoc(JavadocMetaModel jd)
	{
		this.jd = jd;
	}

	public AnnotationsList getAnnotations()
	{
		return ann;
	}

	public void setAnnotations(AnnotationsList ann)
	{
		this.ann = ann;
	}

	public boolean isServoyDocumented()
	{
		return ann.hasAnnotation(ANNOTATION_SERVOY_DOCUMENTED);
	}

	public ClientSupport getServoyClientSupport(MetaModelHolder holder)
	{
		return ClientSupport.fromAnnotation(holder.getAnnotationManager().getAnnotation(this, ANNOTATION_SERVOY_CLIENT_SUPPORT));
	}

	public TypeName getSupertype()
	{
		return supertypeName;
	}

	public List<TypeName> getInterfaces()
	{
		return interfaceNames;
	}

	public List<TypeParameter> getTypeParameters()
	{
		return typeParameters;
	}

	public Set<DocumentationWarning> getWarnings()
	{
		return warnings;
	}

	/**
	 * @return the isInterface
	 */
	public boolean isInterface()
	{
		return isInterface;
	}

	public int compareTo(TypeMetaModel o)
	{
		// first compare the public names
		int publicNames = this.getPublicName().compareTo(o.getPublicName());
		if (publicNames == 0)
		{
			// if the public names are equal compare the qualified names
			return this.name.getQualifiedName().compareTo(o.name.getQualifiedName());
		}
		return publicNames;
	}

	public Collection<IMemberMetaModel> getMembers()
	{
		return members.values();
	}

	public Collection<IMemberMetaModel> getMembers(MetaModelHolder holder)
	{
		if (holder == null)
		{
			return members.values();
		}
		return addMembersRecursively(this, null, holder, new TreeMap<String, IMemberMetaModel>()).values();
	}

	private static Map<String, IMemberMetaModel> addMembersRecursively(TypeMetaModel tmm, ITypeBinding[] typeArguments, MetaModelHolder holder,
		Map<String, IMemberMetaModel> members)
	{
		if (tmm != null)
		{
			for (TypeName intf : tmm.getInterfaces())
			{
				addMembersRecursively(holder.getType(intf), intf.getTypeArguments(), holder, members);
			}

			addMembersRecursively(holder.getType(tmm.getSupertype()), null, holder, members);

			for (Entry<String, IMemberMetaModel> entry : tmm.members.entrySet())
			{
				members.put(entry.getKey(), applyTypeArguments(entry.getValue(), typeArguments, holder));
			}
		}
		return members;
	}

	private static IMemberMetaModel applyTypeArguments(IMemberMetaModel member, ITypeBinding[] typeArguments,
		MetaModelHolder holder)
	{
		if (typeArguments != null && typeArguments.length > 0 && member instanceof MethodMetaModel methodMetaModel)
		{
			List<TypeParameter> typeParameters = methodMetaModel.getClassType(holder).getTypeParameters();
			if (typeParameters.size() == typeArguments.length)
			{
				for (int i = 0; i < typeArguments.length; i++)
				{
					String typeParameterName = typeParameters.get(i).getName().getIdentifier();
					String typeGenericName = methodMetaModel.getType().getQualifiedName();
					if (typeParameterName.equals(typeGenericName))
					{
						return member.withType(typeArguments[i]);
					}
				}

			} // weird they should match
		}

		return member;
	}

	public IMemberMetaModel getMember(String memberName, MetaModelHolder holder)
	{
		IMemberMetaModel member = members.get(memberName);
		if (holder == null || member != null)
		{
			return member;
		}

		TypeMetaModel superType = holder.getType(getSupertype());
		if (superType != null)
		{
			member = superType.getMember(memberName, holder);
		}

		if (member != null)
		{
			return member;
		}

		for (TypeName i : getInterfaces())
		{
			TypeMetaModel intf = holder.getType(i);
			if (intf != null)
			{
				member = intf.getMember(memberName, null);
				if (member != null)
				{
					return member;
				}
			}
		}

		return null;
	}

	public void addMember(String memberName, IMemberMetaModel member)
	{
		members.put(memberName, member);
	}

	public void removeMember(String memberName)
	{
		members.remove(memberName);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		TypeMetaModel other = (TypeMetaModel)obj;
		if (name == null)
		{
			if (other.name != null) return false;
		}
		else if (!name.equals(other.name)) return false;
		return true;
	}


}
