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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Modifier;

/**
 * Holds information about a member of a Java class (field or method):
 * - name of the class
 * - name of the member
 * - visibility of the member
 * - static or not
 * - deprecated flag
 * - Javadoc, if any
 * 
 * @author gerzse
 */
public abstract class MemberMetaModel implements IPublicStore
{
	public static enum Visibility
	{
		Private, Protected, Public
	}

	public static final String ANNOTATION_DEPRECATED = "Deprecated"; //$NON-NLS-1$

	private final String className;
	private final String name;
	private final Visibility visibility;
	private final boolean statc;

	private AnnotationsList annotations = null;
	private JavadocMetaModel javadoc = null;

	/**
	 * Public store where documentation generators can put any kind of data during processing.
	 */
	private final Map<String, Object> store = new HashMap<String, Object>();

	/**
	 * Set of warnings raised while parsing the Javadoc of this member or while solving
	 * its dependencies.
	 */
	private final Set<DocumentationWarning> warnings = new TreeSet<DocumentationWarning>();

	protected MemberMetaModel(String className, String name, BodyDeclaration astNode)
	{
		this.className = className;
		this.name = name;
		Visibility vis = Visibility.Protected;
		for (Object o : astNode.modifiers())
		{
			if (o instanceof Modifier)
			{
				Modifier mod = (Modifier)o;
				if (mod.isPrivate()) vis = Visibility.Private;
				else if (mod.isPublic()) vis = Visibility.Public;
			}
		}
		visibility = vis;
		statc = Modifier.isStatic(astNode.getModifiers());
	}

	public Map<String, Object> getStore()
	{
		return store;
	}

	public String getClassName()
	{
		return className;
	}

	public String getName()
	{
		return name;
	}

	public Visibility getVisibility()
	{
		return visibility;
	}

	public boolean isStatic()
	{
		return statc;
	}

	public boolean isDeprecated()
	{
		return annotations.hasAnnotation(ANNOTATION_DEPRECATED);
	}

	public JavadocMetaModel getJavadoc()
	{
		return javadoc;
	}

	public void setJavadoc(JavadocMetaModel jd)
	{
		this.javadoc = jd;
	}

	public AnnotationsList getAnnotations()
	{
		return annotations;
	}

	public void setAnnotations(AnnotationsList ann)
	{
		this.annotations = ann;
	}

	/**
	 * Members of a class are indexed by a short form of their signature. This method 
	 * returns this short form of the signature.
	 * 
	 * For fields the index signature is the name of the field. For methods it is the
	 * name of the method and the qualified types of its arguments.
	 */
	abstract public String getIndexSignature();

	/**
	 * Checks if this member matches a certain signature.
	 */
	abstract public boolean matchesSignature(String signature);

	/**
	 * Returns the full signature of this member. This is used especially for reporting warnings,
	 * so that the member can be easily identified.
	 */
	abstract public String getFullSignature();

	abstract public TypeName getType();

	public Set<DocumentationWarning> getWarnings()
	{
		return warnings;
	}
}
