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

package com.servoy.eclipse.docgenerator.ui.handler;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.IProgressConstants;
import org.osgi.framework.BundleContext;

import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.ui.Activator;

/**
 * Command handler for generating a documentation XML. The command is enabled for
 * Java packages. One documentation XML is generated for the selected package (and
 * its subpackages).
 * 
 * @author gerzse
 */
public class ExplicitDocumentationGenerationHandler extends AbstractHandler
{

	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		IStructuredSelection selection = (IStructuredSelection)HandlerUtil.getActiveMenuSelection(event);
		if (selection != null)
		{
			IPackageFragment pkg = null;
			Iterator< ? > entries = selection.iterator();
			while (entries.hasNext())
			{
				Object entry = entries.next();
				if (entry instanceof IPackageFragment)
				{
					if (pkg == null)
					{
						pkg = (IPackageFragment)entry;
					}
				}
			}

			if (pkg != null && pkg.getResource() != null && pkg.getResource().getLocation() != null)
			{
				final IPackageFragment pkgFinal = pkg;
				IJavaProject prj = pkg.getJavaProject();

				// Build a map with the selected package and its project.
				final Map<String, List<String>> prjPkg = new HashMap<String, List<String>>();
				List<String> pkgNames = new ArrayList<String>();
				pkgNames.add(pkg.getElementName());
				prjPkg.put(prj.getElementName(), pkgNames);

				final WorkspaceJob docgenJob = new WorkspaceJob("Generating documentation")
				{
					@Override
					public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException
					{
						monitor.beginTask("Generating documentation", 100);

						// Post a documentation generation request.
						DocumentationGenerationRequest req = new DocumentationGenerationRequestFromUI(pkgFinal, false, monitor)
						{
							@Override
							protected boolean isRunningModal()
							{
								Boolean b = (Boolean)getProperty(IProgressConstants.PROPERTY_IN_DIALOG);
								return b != null ? b.booleanValue() : false;
							}

							@Override
							protected void postponeAction(Action act)
							{
								setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
								setProperty(IProgressConstants.ACTION_PROPERTY, act);
							}
						};
						Dictionary<String, String> props = new Hashtable<String, String>();
						final BundleContext context = Activator.getDefault().getBundle().getBundleContext();
						context.registerService(DocumentationGenerationRequest.class.getName(), req, props);

						return Status.OK_STATUS;
					}
				};
				docgenJob.setUser(true);
				docgenJob.schedule();
			}
		}
		return null;
	}
}
