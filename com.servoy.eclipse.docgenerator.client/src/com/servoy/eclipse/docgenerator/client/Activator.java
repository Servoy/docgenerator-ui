/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2011 Servoy BV

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

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.servoy.eclipse.docgenerator.service.LogUtil;

/**
 * This plugin is used internally be Servoy to build the documentation for relevant classes.
 * It can be used as an example of how to post a DocumentationGenerationRequest and how to
 * interpret the answer.
 * 
 * @author gerzse
 */

public class Activator extends Plugin
{
	public static final String PLUGIN_ID = "com.servoy.eclipse.docgenerator.client";

	private static Activator plugin;

	public Activator()
	{
	}

	/**
	 * The plugin receives its parameters through the BundleContext.
	 */
	@Override
	public void start(final BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;
		LogUtil.logger().fine(PLUGIN_ID + " bundle starting...");
	}

	@Override
	public void stop(BundleContext context) throws Exception
	{
		LogUtil.logger().fine(PLUGIN_ID + " bundle stopping...");
		plugin = null;
		super.stop(context);
		LogUtil.logger().fine(PLUGIN_ID + " bundle stopped.");
	}

	public static Activator getDefault()
	{
		return plugin;
	}
}
