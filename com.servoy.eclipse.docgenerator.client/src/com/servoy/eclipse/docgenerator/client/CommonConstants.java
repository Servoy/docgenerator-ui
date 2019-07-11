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

package com.servoy.eclipse.docgenerator.client;

/**
 * Set of constants that are used when passing information from outside the OSGi framework
 * to the documentation client bundle.
 * 
 * @author gerzse
 */
public class CommonConstants
{
	public static String PROP_PROJECTS_TO_DOCUMENT = "servoy.docs.documented.projects";
	public static String PROP_OUTPUT_FILE = "servoy.docs.output";
	public static String PROP_DOC_GENERATOR_ID = "servoy.docs.docgenerator.id";
	public static String PROP_CATEGORIES = "servoy.docs.categories";
	public static String PROP_AUTOPILOT = "servoy.docs.autopilot";
	public static String PROP_MAP_UNDOCUMENTED_TYPES = "servoy.docs.map.undocumented.types";

	public static String PROJECTS_SEPARATOR = ";";
	public static String PROJECT_PACKAGES_SEPARATOR = ":";
	public static String PACKAGES_SEPARATOR = ",";
}
