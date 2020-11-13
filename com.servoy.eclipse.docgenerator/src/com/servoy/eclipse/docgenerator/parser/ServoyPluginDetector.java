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

package com.servoy.eclipse.docgenerator.parser;

import java.util.List;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;
import com.servoy.eclipse.docgenerator.service.LogUtil;

/**
 * Quick AST visitor to determine if inside a certain package there is any Servoy client plugin.
 * A Servoy client plugin is a class that directly implements the com.servoy.j2db.plugins.IClientPlugin interface.
 *
 * @author gerzse
 */
public class ServoyPluginDetector extends ASTVisitor
{
	private boolean pluginFound;

	public boolean containsPlugin()
	{
		return pluginFound;
	}

	@Override
	public boolean visit(FieldDeclaration node)
	{
		return false;
	}

	@Override
	public boolean visit(ImportDeclaration node)
	{
		return false;
	}

	@Override
	public boolean visit(Javadoc node)
	{
		return false;
	}

	@Override
	public boolean visit(MethodDeclaration node)
	{
		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node)
	{
		pluginFound |= isServoyPlugin(node);
		// If a plugin was found, don't go inside types anymore.
		return !pluginFound;
	}

	private boolean isServoyPlugin(TypeDeclaration clz)
	{
		List< ? > superInterfaces = clz.superInterfaceTypes();
		if (superInterfaces != null)
		{
			for (Object o : superInterfaces)
			{
				if (o instanceof Type)
				{
					Type interf = (Type)o;
					TypeName tni = new TypeName(interf, false, clz.getName().getFullyQualifiedName(), "interface", new TreeSet<DocumentationWarning>());
					if (tni.getQualifiedName() != null)
					{
						if (tni.getQualifiedName().equals("com.servoy.j2db.plugins.IClientPlugin"))
						{
							LogUtil.logger().fine("Found a class that is Servoy plugin: '" + clz.getName().getFullyQualifiedName() + "'.");
							return true;
						}
						else if (tni.getQualifiedName().equals("com.servoy.j2db.dataui.IServoyAwareBean") ||
							tni.getQualifiedName().equals("com.servoy.j2db.dataui.IServoyAwareVisibilityBean"))
						{
							LogUtil.logger().fine("Found a class that is Servoy-aware bean: '" + clz.getName().getFullyQualifiedName() + "'.");
							return true;
						}
						else if (tni.getQualifiedName().equals("com.servoy.j2db.IServoyBeanFactory") ||
							tni.getQualifiedName().equals("com.servoy.j2db.IServoyBeanFactory2"))
						{
							LogUtil.logger().fine("Found a class that is Servoy bean factory: '" + clz.getName().getFullyQualifiedName() + "'.");
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
