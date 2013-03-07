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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;


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
@SuppressWarnings("nls")
public class TypeMetaModel implements Comparable<TypeMetaModel>, IPublicStore
{
	Map<String, IMemberMetaModel> members = new TreeMap<String, IMemberMetaModel>();

	/**
	 * Default category to use when the "category" attribute is not explicitly given in the @ServoyDocumented annotation.
	 * 
	 * @see com.servoy.j2db.documentation.ServoyDocumented.PLUGINS
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
	 * warnings that correspond to those members that will apear in the generated XML).
	 */
	private final Set<DocumentationWarning> warnings = new TreeSet<DocumentationWarning>();

	public TypeMetaModel(String packageName, List<String> ancestorClassNames, TypeDeclaration astNode)
	{
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
					Type interf = (Type)o;
					TypeName tni = new TypeName(interf, false, name.getQualifiedName(), "interface", warnings);
					interfaceNames.add(tni);
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
		return getIntAttribute(ATTRIBUTE_DISPLAY_TYPE, 0);
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

	private ClientSupport getServoyClientSupport(TypeMetaModel tmm, MetaModelHolder holder)
	{
		if (tmm == null) return null;

		if (tmm.getAnnotations() != null)
		{
			AnnotationMetaModel csp = tmm.getAnnotations().getAnnotation(ANNOTATION_SERVOY_CLIENT_SUPPORT);
			if (csp != null) return ClientSupport.fromAnnotation(csp);

			if (tmm.getAnnotations().hasAnnotation(ANNOTATION_SERVOY_MOBILE_FILTER_OUT))
			{
				return ClientSupport.wc_sc;
			}

			if (tmm.getAnnotations().hasAnnotation(ANNOTATION_SERVOY_MOBILE))
			{
				return ClientSupport.mc_wc_sc;
			}
		}

		TypeName aux = null;
		Iterator<TypeName> it = tmm.getInterfaces().iterator();
		for (aux = tmm.getSupertype();; aux = it.next())
		{
			if (aux != null)
			{
				ClientSupport scp = getServoyClientSupport(holder.getType(aux.getQualifiedName()), holder);
				if (scp != null)
				{
					return scp;
				}
			}
			if (!it.hasNext()) break;
		}
		return null;
	}

	private boolean hasServoyMobileFilterOutAnnotation(TypeMetaModel tmm, MetaModelHolder holder)
	{
		if (tmm == null) return false;

		if (tmm.getAnnotations() != null && tmm.getAnnotations().hasAnnotation(ANNOTATION_SERVOY_MOBILE_FILTER_OUT))
		{
			return true;
		}

		TypeName aux = null;
		Iterator<TypeName> it = tmm.getInterfaces().iterator();
		for (aux = tmm.getSupertype();; aux = it.next())
		{
			if (aux != null)
			{
				TypeMetaModel src = holder.getType(aux.getQualifiedName());
				if (src != null && hasServoyMobileFilterOutAnnotation(src, holder))
				{
					return true;
				}
			}
			if (!it.hasNext()) break;
		}
		return false;
	}

	public boolean hasServoyMobileFilterOutAnnotation(MetaModelHolder holder)
	{
		return hasServoyMobileFilterOutAnnotation(this, holder);
	}

	public ClientSupport getServoyClientSupport(MetaModelHolder holder)
	{
		return getServoyClientSupport(this, holder);
	}

	public TypeName getSupertype()
	{
		return supertypeName;
	}

	public List<TypeName> getInterfaces()
	{
		return interfaceNames;
	}

	public Set<DocumentationWarning> getWarnings()
	{
		return warnings;
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

	public boolean hasMember(String memberName)
	{
		return members.containsKey(memberName);
	}

	public IMemberMetaModel getMember(String memberName)
	{
		return members.get(memberName);
	}

	public void addMember(String memberName, IMemberMetaModel member)
	{
		members.put(memberName, member);
	}

	public void removeMember(String memberName)
	{
		members.remove(memberName);
	}

}
