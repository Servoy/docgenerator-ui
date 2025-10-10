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

import java.util.Map;

/**
 * @author gerzse
 */
public interface IPublicStore
{
	String ANNOTATION_SERVOY_DOCUMENTED = "ServoyDocumented";
	String ANNOTATION_DEPRECATED = "Deprecated";
	String ANNOTATION_SERVOY_CLIENT_SUPPORT = "ServoyClientSupport";
	String JS_REAL_CLASS = "JSRealClass";

	Map<String, Object> getStore();
}
