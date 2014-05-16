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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.servoy.eclipse.docgenerator.DocumentationBuilder;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;
import com.servoy.eclipse.docgenerator.ui.Activator;

/**
 * Documentation generation request posted from the UI for the doc generator.
 * 
 * @author gerzse
 */
public abstract class DocumentationGenerationRequestFromUI implements DocumentationGenerationRequest
{
	int lastPercent = 0;
	boolean confirmAll = false;
	boolean canceled = false;

	private final boolean autopilot;
	private final Map<String, List<String>> prjPkg;
	private final IPath outputFile;
	private final IProgressMonitor monitor;

	public DocumentationGenerationRequestFromUI(IPackageFragment pkg, boolean autopilot, IProgressMonitor monitor)
	{
		IJavaProject prj = pkg.getJavaProject();
		this.autopilot = autopilot;
		if (!autopilot)
		{
			// If autopilot is disabled, we generate a single XML file for the entire tree rooted at the given package.
			outputFile = pkg.getResource().getFullPath().append(DocumentationBuilder.EXTENSION_XML_FILE);
		}
		else
		{
			outputFile = null;
		}

		// Build a map with the selected package and its project.
		prjPkg = new HashMap<String, List<String>>();
		List<String> pkgNames = new ArrayList<String>();
		pkgNames.add(pkg.getElementName());
		prjPkg.put(prj.getElementName(), pkgNames);

		this.monitor = monitor;
	}

	public Map<String, List<String>> getProjectsAndPackagesToDocument()
	{
		return prjPkg;
	}

	public Set<String> getCategoryFilter()
	{
		// no filtering when invoking from the UI
		return null;
	}

	public boolean autopilot()
	{
		return autopilot;
	}

	public boolean tryToMapUndocumentedTypes()
	{
		// no mapping of types when running from the UI
		return false;
	}

	public IPath getOutputFile()
	{
		return outputFile;
	}

	public String getDocumentationGeneratorID()
	{
		// we want to invoke the default generator
		return null;
	}

	public boolean confirmResourceOverwrite(final IPath path)
	{
		// If the settings say to not ask for confirmation, then just say yes.
		boolean mustAsk = Activator.getDefault().getPreferenceStore().getBoolean(Activator.ASK_FOR_FILE_OVERWRITE_PREFERENCE);
		if (!mustAsk)
		{
			return true;
		}

		// If the user already clicked "Confirm All" then just say yes.
		if (confirmAll)
		{
			return true;
		}

		// Ask the user if it's OK to overwrite.
		final boolean choice[] = new boolean[1];
		Display.getDefault().syncExec(new Runnable()
		{
			public void run()
			{
				MessageDialog dlg = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Confirm file overwrite", null,
					"The following file already exists and will be overwritten: '" + path.toOSString() +
						"'. Are you sure you want the file to be overwritten?" + "\n\n" +
						"You can disable this confirmation dialog from the plugin preferences page.", MessageDialog.CONFIRM,
					new String[] { IDialogConstants.NO_LABEL, IDialogConstants.YES_LABEL, IDialogConstants.YES_TO_ALL_LABEL, IDialogConstants.CANCEL_LABEL }, 3);
				int result = dlg.open();
				if (result == 2) // the "Yes to All" button
				{
					confirmAll = true;
				}
				if (result == 3) // the "Cancel" button
				{
					canceled = true;
				}
				choice[0] = result == 1 || result == 2; // The "Yes" or "Yes to All" buttons were pressed.
			}
		});
		return choice[0];
	}

	public void progressUpdate(int percentDone)
	{
		LogUtil.logger().fine("Progress update: " + percentDone + "%");
		if (percentDone > lastPercent)
		{
			int delta = percentDone - lastPercent;
			monitor.worked(delta);
			lastPercent = percentDone;
		}
	}

	public void postProcess(MetaModelHolder docs, IPath actualOutputFile)
	{
		// No post-processing when running from GUI.
	}

	public void requestHandled(List<IPath> xmlFiles, List<IPath> warningsFiles, List<Throwable> exceptions, boolean canceled)
	{
		LogUtil.logger().fine("Documentation generated.");
		monitor.done();
		final StringBuffer sb = new StringBuffer();
		boolean showSummarry = Activator.getDefault().getPreferenceStore().getBoolean(Activator.SHOW_FINAL_SUMMARY);
		if (showSummarry)
		{
			if (xmlFiles != null && xmlFiles.size() > 0)
			{
				if (xmlFiles.size() == 1)
				{
					sb.append("The following documentation XML file was generated:\n");
					sb.append(xmlFiles.get(0).toOSString()).append("\n");
				}
				else
				{
					sb.append(xmlFiles.size()).append(" documentation XML files were generated.\n");
				}
			}
			else
			{
				sb.append("No documentation XML files were generated. This can mean that you don't have any Servoy client plugin or bean in the selected package hierarchy.\n");
			}
			if (warningsFiles != null && warningsFiles.size() > 0)
			{
				if (warningsFiles.size() == 1)
				{
					sb.append("Warnings encountered while analyzing the Javadocs were written to the following file:\n");
					sb.append(warningsFiles.get(0).toOSString()).append("\n");
				}
				else
				{
					sb.append(warningsFiles.size()).append(" files were generated containing warnings encountered while analyzing the Javadocs.\n");
				}
			}
		}
		// Exceptions are displayed regardless of the setting related to the final summary.
		if (exceptions != null && exceptions.size() > 0)
		{
			sb.append(exceptions.size()).append(" exception(s) occured while generating documentation. Please analyze the log file for details.\n");
		}
		if (showSummarry)
		{
			if (canceled)
			{
				sb.append("The documentation generation was canceled.");
			}
		}

		// If there is some summary to show, do it.
		if (sb.length() > 0)
		{
			if (showSummarry)
			{
				sb.append("\n\n").append("You can disable this summary dialog from the plugin preferences page.");
			}
			// Show the result in the GUI.
			final Action act = new Action("Documentation generation results")
			{
				@Override
				public void run()
				{
					MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Documentation generation report",
						sb.toString());
				}
			};
			if (isRunningModal())
			{
				Display.getDefault().asyncExec(new Runnable()
				{
					public void run()
					{
						act.run();
					}
				});
			}
			else
			{
				postponeAction(act);
			}
		}
	}

	public boolean cancelRequested()
	{
		return monitor.isCanceled() || canceled;
	}

	// To be done in the Job context. We can't get access to the Job context from inner class.
	abstract protected boolean isRunningModal();

	// To be done in the Job context. We can't get access to the Job context from inner class.
	abstract protected void postponeAction(Action act);

}
