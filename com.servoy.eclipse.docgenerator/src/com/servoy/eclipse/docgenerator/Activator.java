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

package com.servoy.eclipse.docgenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import com.servoy.eclipse.docgenerator.generators.IDocumentationGenerator;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;

@SuppressWarnings("nls")
public class Activator extends Plugin
{
	public static final String PLUGIN_ID = "com.servoy.eclipse.docgenerator";

	private static Activator plugin;

	private static Map<String, IDocumentationGenerator> generators = new HashMap<String, IDocumentationGenerator>();

	public Activator()
	{
	}

	@Override
	public void start(final BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;

		LogUtil.logger().fine(PLUGIN_ID + " bundle starting...");

		// Load all extensions.
		loadExtensions();

		// Register service listener to listen to documentation generation
		// requests.
		ServiceListener listener = new ServiceListener()
		{
			public void serviceChanged(ServiceEvent event)
			{
				ServiceReference ref = event.getServiceReference();
				switch (event.getType())
				{
					case ServiceEvent.REGISTERED :
						handleNewService(context, ref);
						break;
				}
			}
		};
		String filter = "(objectclass=" + DocumentationGenerationRequest.class.getName() + ")";
		try
		{
			context.addServiceListener(listener, filter);
			// Send any service instances that maybe got registered earlier.
			ServiceReference[] existing = context.getServiceReferences(null, filter);
			if (existing != null)
			{
				for (ServiceReference sr : existing)
				{
					listener.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sr));
				}
			}
			LogUtil.logger().fine("Service listener registered.");
		}
		catch (Exception e)
		{
			LogUtil.logger().log(Level.SEVERE, "Exception while registering service listener.", e);
		}
		LogUtil.logger().fine(PLUGIN_ID + " bundle started.");
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

	public IDocumentationGenerator getGenerator(String id)
	{
		return generators.get(id);
	}

	private void loadExtensions()
	{
		IExtensionRegistry reg = Platform.getExtensionRegistry();
		IExtensionPoint ep = reg.getExtensionPoint(IDocumentationGenerator.EXTENSION_ID);
		if (ep != null)
		{
			LogUtil.logger().fine("Found extension point.");
			IExtension[] extensions = ep.getExtensions();
			if (extensions != null)
			{
				for (IExtension e : extensions)
				{
					IConfigurationElement[] ce = e.getConfigurationElements();
					if (ce != null)
					{
						for (IConfigurationElement cc : ce)
						{
							try
							{
								IDocumentationGenerator generator = (IDocumentationGenerator)cc.createExecutableExtension("class");
								generators.put(generator.getID(), generator);
								LogUtil.logger().fine("Registered generator '" + generator.getID() + "' from extension point.");
							}
							catch (CoreException e1)
							{
								LogUtil.logger().log(Level.SEVERE, "Exception while loading documentation generator from extension point.", e1);
							}
						}
					}
				}
			}
		}
		else
		{
			LogUtil.logger().warning("Did not find extension point.");
		}
	}

	private void handleNewService(BundleContext context, ServiceReference ref)
	{
		DocumentationGenerationRequest req = (DocumentationGenerationRequest)context.getService(ref);
		if (req != null)
		{
			try
			{
				if (LogUtil.logger().isLoggable(Level.FINE))
				{
					StringBuffer sb = new StringBuffer();
					Map<String, List<String>> projectsAndPackages = req.getProjectsAndPackagesToDocument();
					sb.append("Got a request for ").append(projectsAndPackages.size()).append(" projects to document");
					for (String prjName : projectsAndPackages.keySet())
					{
						List<String> packageNames = projectsAndPackages.get(prjName);
						sb.append("\n\t").append(prjName).append(":");
						for (String pkgName : packageNames)
							sb.append(" ").append(pkgName);
					}
					LogUtil.logger().fine(sb.toString());
				}

				DocumentationBuilder xmlBuilder = new DocumentationBuilder(req);
				xmlBuilder.build();
			}
			catch (Exception e)
			{
				LogUtil.logger().log(Level.SEVERE, "Exception while handling request.", e); //$NON-NLS-1$
			}
		}
		else
		{
			LogUtil.logger().log(Level.SEVERE, "Cannot retrieve service."); //$NON-NLS-1$
		}
	}
}
