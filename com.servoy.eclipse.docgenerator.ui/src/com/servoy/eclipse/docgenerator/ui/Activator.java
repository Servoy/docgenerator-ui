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

package com.servoy.eclipse.docgenerator.ui;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin
{
	// Preference key related to asking the user before overwriting a file.
	public final static String ASK_FOR_FILE_OVERWRITE_PREFERENCE = "ask_for_file_overwrite"; //$NON-NLS-1$
	// Preference key related to showing a final summary popup with how many files were created, etc.
	public final static String SHOW_FINAL_SUMMARY = "show_final_summary"; //$NON-NLS-1$

	// The plug-in ID
	public static final String PLUGIN_ID = "com.servoy.eclipse.docgenerator.ui"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	@Override
	public void start(BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception
	{
		plugin = null;
		super.stop(context);
	}

	public static Activator getDefault()
	{
		return plugin;
	}

	@Override
	protected void initializeDefaultPreferences(IPreferenceStore store)
	{
		store.setDefault(ASK_FOR_FILE_OVERWRITE_PREFERENCE, true);
		store.setDefault(SHOW_FINAL_SUMMARY, true);
	}
}
