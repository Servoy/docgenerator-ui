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

package com.servoy.eclipse.docgenerator.generators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
//import org.eclipse.m2e.core.MavenPlugin;
//import org.eclipse.m2e.jdt.MavenJdtPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import com.servoy.eclipse.docgenerator.Activator;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.parser.JavadocExtractor;
import com.servoy.eclipse.docgenerator.parser.ServoyPluginDetector;
import com.servoy.eclipse.docgenerator.parser.SourceCodeTracker;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;


/**
 * Super class for all documentation generators.
 * @author emera
 */
public abstract class AbstractDocumentationGenerator implements IDocumentationGenerator, IApplication
{
	private static final String SERVOY_TARGET = "/eclipse_target/servoy.target";

	private static enum ScanState
	{
		None, BundleFolders, Project, Packages, Workspace, OutputFile, DocGeneratorId, Categories, ImportProjects
	}

	// Command-line options.
	private static final String WORKSPACE_PATH = "--workspace";
	private static final String PROJECT = "--project";
	private static final String OUTPUT_FILE = "--output-file";
	private static final String AUTOPILOT = "--autopilot";
	private static final String MAP_UNDOCUMENTED_TYPES = "--map-undocumented-types";
	private static final String CATEGORIES = "--categories";
	private static final String IMPORT_PROJECTS = "--import-projects"; //if present, the projects are imported into the workspace

	/**
	 * The name of the XML file which holds documentation and other Servoy extension related info.
	 */
	public static final String EXTENSION_XML_FILE = "servoy-extension.xml";

	private DocumentationGenerationRequest req;
	private ASTParser parser;
	protected IWorkspaceRoot workspaceRoot;

	@Override
	public Object start(IApplicationContext context) throws Exception
	{
		try
		{
			//force start of the servoy shared bundle
			Bundle bundle = Platform.getBundle("servoy_shared");
			if (bundle != null) bundle.start();
//			com.servoy.j2db.persistence.Field.class.getCanonicalName();
//			com.servoy.j2db.persistence.Form.class.getCanonicalName();
//			StaticContentSpecLoader.getContentSpec();
			//and dltk
			Platform.getBundle("org.eclipse.dltk.javascript.rhino").start();
			org.mozilla.javascript.EvaluatorException.class.getCanonicalName();
			org.mozilla.javascript.Parser.class.getCanonicalName();
			org.mozilla.javascript.ast.AstNode.class.getCanonicalName();
			org.mozilla.javascript.ast.Label.class.getCanonicalName();
			org.mozilla.javascript.ast.Scope.class.getCanonicalName();
			org.mozilla.javascript.ast.Name.class.getCanonicalName();
		}
		catch (BundleException e)
		{
			LogUtil.logger().log(Level.SEVERE, e.getMessage(), e);
		}

//		System.err.println("Maven local repo path: " + MavenPlugin.getMaven().getLocalRepositoryPath());
		// start the maven jdt plugin so that the m2 repo classpath variables and containers are setup.
//		MavenJdtPlugin.getDefault();

		String[] args = (String[])context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		parser = ASTParser.newParser(AST.JLS3);
		req = parseArgs(args);

		workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		List<IProject> importedProjects = new ArrayList<IProject>();
		List<IProject> existingClosedProjects = new ArrayList<IProject>();
		if (req.importProjects())
		{
			File wr = workspaceRoot.getLocation().toFile();
			importExistingAndOpenClosedProjects(wr, importedProjects, existingClosedProjects);
			refreshProjects();
		}

		build();

		if (req.importProjects()) restoreImportedAndClosedProjects(importedProjects, existingClosedProjects);

		return IApplication.EXIT_OK;
	}

	/**
	 * @param args
	 * @return
	 */
	private DocumentationGenerationRequest parseArgs(String[] args)
	{
		Map<String, List<String>> projectsAndPackages = new HashMap<String, List<String>>();
		String workspace = null;
		String outputFile = null;
		ScanState state = ScanState.None;
		String prjName;
		List<String> packageNames = null;
		Set<String> categories = new TreeSet<String>();
		boolean isAutopilot = false;
		boolean importProjects = false;
		boolean doMapUndocumentedTypes = false;
		Set<String> bundleFolders = new HashSet<String>();
		for (String arg : args)
		{
			if (PROJECT.equals(arg))
			{
				state = ScanState.Project;
			}
			else if (OUTPUT_FILE.equals(arg))
			{
				state = ScanState.OutputFile;
			}
			else if (WORKSPACE_PATH.equals(arg))
			{
				state = ScanState.Workspace;
			}
			else if (CATEGORIES.equals(arg))
			{
				state = ScanState.Categories;
			}
			else if (AUTOPILOT.equals(arg))
			{
				isAutopilot = true;
			}
			else if (IMPORT_PROJECTS.equals(arg))
			{
				state = ScanState.ImportProjects;
			}
			else if (MAP_UNDOCUMENTED_TYPES.equals(arg))
			{
				doMapUndocumentedTypes = true;
			}
			else
			{
				if (state == ScanState.Project)
				{
					prjName = arg;
					if (!projectsAndPackages.containsKey(prjName))
					{
						packageNames = new ArrayList<String>();
						projectsAndPackages.put(prjName, packageNames);
					}
					else
					{
						packageNames = projectsAndPackages.get(prjName);
					}
					state = ScanState.Packages;
				}
				else if (state == ScanState.Packages)
				{
					if (packageNames != null)
					{
						packageNames.add(arg);
					}
				}
				else if (state == ScanState.Categories)
				{
					categories.add(arg);
				}
				else if (state == ScanState.ImportProjects)
				{
					importProjects = Boolean.parseBoolean(arg);
				}
				else
				{
					File f = new File(arg);
					try
					{
						String canon = f.getCanonicalPath();
						if (state == ScanState.BundleFolders)
						{
							if (!bundleFolders.contains(canon)) bundleFolders.add(canon);
						}
						else if (state == ScanState.Workspace)
						{
							workspace = canon;
						}
						else if (state == ScanState.OutputFile)
						{
							outputFile = canon;
						}
					}
					catch (IOException e)
					{
						LogUtil.logger().log(Level.SEVERE, "Exception while canonicalizing path '" + f.getAbsolutePath() + "'.", e);
					}
				}
			}
		}
		if (outputFile == null && !isAutopilot)
		{
			LogUtil.logger().log(Level.SEVERE, "Incorrect arguments. The output file is null and the autopilot flag is missing.");
			System.exit(1);
		}
		if (workspace == null || projectsAndPackages.isEmpty())
		{
			LogUtil.logger().log(Level.SEVERE, "Incorrect arguments. The output file is null and the autopilot flag is missing.");
			System.exit(1);
		}

		return createDocumentationGenerationRequest(projectsAndPackages, isAutopilot, doMapUndocumentedTypes, outputFile, categories, importProjects,
			workspace);
	}


	/**
	 * @param importProjects
	 * @param workspace
	 * @return
	 */
	private DocumentationGenerationRequest createDocumentationGenerationRequest(final Map<String, List<String>> projectsAndPackages, final boolean isAutopilot,
		final boolean mapUndocumentedTypes, final String outputFile, final Set<String> categories, final boolean importProjects, final String workspace)
	{
		DocumentationGenerationRequest request = new DocumentationGenerationRequest()
		{
			public Map<String, List<String>> getProjectsAndPackagesToDocument()
			{
				return projectsAndPackages;
			}

			public Set<String> getCategoryFilter()
			{
				return categories.size() > 0 ? categories : null;
			}

			public boolean autopilot()
			{
				return isAutopilot;
			}

			public boolean tryToMapUndocumentedTypes()
			{
				return mapUndocumentedTypes;
			}

			public IPath getOutputFile()
			{
				return outputFile != null ? new Path(outputFile) : null;
			}

			public String getDocumentationGeneratorID()
			{
				return getID();
			}

			public boolean confirmResourceOverwrite(IPath path)
			{
				// Just accept overwriting, but print a message to stdout.
				LogUtil.logger().info("The following resource will be overwritten: '" + workspaceRoot.getFile(path).getLocation() + "'.");
				return true;
			}

			public void progressUpdate(int percentDone)
			{
				LogUtil.logger().fine("Progress update: " + percentDone + "%");
			}

			public void postProcess(MetaModelHolder docs, IPath actualOutputFile)
			{
				if (actualOutputFile != null)
				{
					IPath sourceCodeReportPath = actualOutputFile.removeFileExtension().addFileExtension("html");
					SampleCodeAnalyzer analyzer = new SampleCodeAnalyzer(docs, this, sourceCodeReportPath);
					analyzer.analyzeAndReport();
				}
				DocBeautifier db = new DocBeautifier(docs);
				db.beautify();
			}

			public void requestHandled(List<IPath> xmlFiles, List<IPath> warningsFiles, List<Throwable> exceptions, boolean canceled)
			{
				LogUtil.logger().fine("Documentation generated.");
				StringBuffer message = new StringBuffer();
				if (xmlFiles != null && xmlFiles.size() > 0)
				{
					message.append("The following XML files were generated:\n");
					for (IPath p : xmlFiles)
					{
						message.append(workspaceRoot.getFile(p).getLocation()).append("\n");
					}
				}
				else
				{
					message.append("No XML files were generated.\n");
				}
				if (warningsFiles != null && warningsFiles.size() > 0)
				{
					message.append("The following warnings files were generated:\n");
					for (IPath p : warningsFiles)
					{
						message.append(workspaceRoot.getFile(p).getLocation()).append("\n");
					}
				}
				else
				{
					message.append("No warning files were generated.\n");
				}
				if (exceptions != null && exceptions.size() > 1)
				{
					message.append("The following exceptions were encountered:\n");
					for (Throwable e : exceptions)
					{
						message.append(e.getMessage()).append("\n");
					}
				}
				LogUtil.logger().info(message.toString());
			}

			public boolean cancelRequested()
			{
				// Never cancel.
				return false;
			}

			@Override
			public boolean importProjects()
			{
				return importProjects;
			}
		};
		return request;
	}

	@Override
	public void stop()
	{
	}

	public void build()
	{
		Date start = Calendar.getInstance().getTime();
		LogUtil.logger().fine("Documentation build started at " + start.toString() + ".");

		List<IPath> xmlFiles = new ArrayList<IPath>();
		List<IPath> warningsFiles = new ArrayList<IPath>();
		List<Throwable> exceptions = new ArrayList<Throwable>();
		try
		{
			// If autopilot is on, then scan all packages and find all topmost packages that
			// contain a Servoy plugin (a class that implements IClientPlugin). Then generate
			// separate documentation XMLs for each of these found packages.
			if (req.autopilot())
			{
				List<String> extraToProcessProjectNames = new ArrayList<String>();
				List<String> extraToProcessPackageNames = new ArrayList<String>();

				List<String> toProcessProjectNames = new ArrayList<String>();
				List<String> toProcessPackageNames = new ArrayList<String>();
				List<IPath> toProcessXmlFiles = new ArrayList<IPath>();
				LogUtil.logger().fine("Autopilot is on. Scanning for packages that contain Servoy plugins...");
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				IWorkspaceRoot root = workspace.getRoot();
				LogUtil.logger().fine("Workspace root is '" + root.getLocation().toPortableString() + "'.");
				IProject[] projects = root.getProjects();
				LogUtil.logger().fine("There are " + projects.length + " projects in the workspace.");
				for (int i = 0; i < projects.length && !req.cancelRequested(); i++)
				{
					IProject prj = projects[i];
					if (prj.isOpen() && prj.isNatureEnabled("org.eclipse.jdt.core.javanature"))
					{
						if (req.getProjectsAndPackagesToDocument().containsKey(prj.getName()))
						{
							Set<String> visitedPackages = new HashSet<String>();
							List<String> packages = req.getProjectsAndPackagesToDocument().get(prj.getName());
							IJavaProject jPrj = JavaCore.create(prj);
							IPackageFragment[] fragments = jPrj.getPackageFragments();
							for (int j = 0; j < fragments.length && !req.cancelRequested(); j++)
							{
								IPackageFragment pkg = fragments[j];
								if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE)
								{
									String thisPackageName = pkg.getElementName();
									// check if the current package is listed among the packages
									// to document for the current project (or is a subpackage of
									// a listed package)
									boolean process = false;
									for (String prefix : packages)
									{
										if (matchesPackage(thisPackageName, prefix))
										{
											process = true;
											break;
										}
									}
									if (process)
									{
										// First check if the current package has at least one class that is
										// a Servoy plugin.
										ServoyPluginDetector pluginDetectorVisitor = new ServoyPluginDetector();
										ICompilationUnit[] units = pkg.getCompilationUnits();
										for (int k = 0; k < units.length && !req.cancelRequested() && !pluginDetectorVisitor.containsPlugin(); k++)
										{
											ICompilationUnit comp = units[k];

											// We must reconfigure the parser each time, because createAST clears all settings.
											parser.setResolveBindings(true);
											parser.setIgnoreMethodBodies(true);
											parser.setProject(pkg.getJavaProject());
											parser.setSource(comp);
											parser.setBindingsRecovery(true);

											// Create the AST and send it to the visitor.
											ASTNode cu = parser.createAST(null);
											cu.accept(pluginDetectorVisitor);
										}
										if (!req.cancelRequested())
										{
											// If the current package should be documented, then schedule it for processing,
											// unless it has a parent package that also needs to be processed.
											if (pluginDetectorVisitor.containsPlugin())
											{
												boolean hasVisitedParent = false;
												for (String parent : visitedPackages)
												{
													if (thisPackageName.startsWith(parent + "."))
													{
														hasVisitedParent = true;
														break;
													}
												}
												if (!hasVisitedParent)
												{
													LogUtil.logger().fine("Will process package '" + thisPackageName + "' in project '" + prj.getName() +
														"' because it contains a Servoy plugin and autopilot is on.");
													visitedPackages.add(thisPackageName);
													toProcessProjectNames.add(prj.getName());
													toProcessPackageNames.add(thisPackageName);
													IPath xmlFile = pkg.getResource().getFullPath().append(EXTENSION_XML_FILE);
													toProcessXmlFiles.add(xmlFile);
												}
												else
												{
													LogUtil.logger().fine("Skipping package '" + thisPackageName + "' in project '" + prj.getName() +
														"' because it contains a Servoy plugin, but a parent package also contains a Servoy plugin.");
												}
											}
											else
											{
												// when documenting plugins with mobile client support, we take non-plugin packages
												// as well (may contain needed interfaces)
												// currently this is hardcoded to take in only servoy_base plugin's project
												// comment the check below to allow all projects/packages
												if (thisPackageName.contains("base.plugins") && prj.getName().contains("servoy_base"))
												{
													extraToProcessProjectNames.add(prj.getName());
													extraToProcessPackageNames.add(thisPackageName);
													LogUtil.logger().fine("Will process package '" + thisPackageName + "' in project '" + prj.getName() +
														"' needed to document plugins with mobile client support.");
												}

												else
												{
													LogUtil.logger().fine("Skipping package '" + thisPackageName + "' in project '" + prj.getName() +
														"' because it does not contain a Servoy plugin and autopilot is on.");
												}
											}

										}
									}
									else
									{
										LogUtil.logger().fine("Skipping package '" + pkg.getElementName() + "' in project '" + prj.getName() +
											"' because it was not listed among the packages to document.");
									}
								}
							}
						}
						else
						{
							LogUtil.logger().fine("Skipping project '" + prj.getName() + "' because it is not listed among the projects to document.");
						}
					}
					else
					{
						LogUtil.logger().fine("Skipping roject '" + prj.getName() + "' because it is not a Java project.");
					}
				}
				req.progressUpdate(0);

				if (toProcessProjectNames.size() > 0)
				{
					// Process all scheduled packages.
					int delta = 100 / toProcessProjectNames.size();
					for (int i = 0; i < toProcessProjectNames.size() && !req.cancelRequested(); i++)
					{
						String projectName = toProcessProjectNames.get(i);
						String packageName = toProcessPackageNames.get(i);
						IPath xmlFile = toProcessXmlFiles.get(i);
						// Prepare a dummy map with one entry (the current project) and one package for
						// the project.
						Map<String, List<String>> packagesToVisit = new HashMap<String, List<String>>();
						List<String> listWithThisPackage = new ArrayList<String>();
						listWithThisPackage.add(packageName);
						packagesToVisit.put(projectName, listWithThisPackage);
						IPath warningsFile = xmlFile.removeFileExtension().addFileExtension("warnings.txt");
						int startPercent = i * delta;
						int endPercent = startPercent + delta - 1;

						// this is to include any extra projects/packages that may be needed when documenting plugins with mobile client support
						if (extraToProcessProjectNames.size() > 0)
						{
							for (int x = 0; x < extraToProcessProjectNames.size() && !req.cancelRequested(); x++)
							{
								projectName = extraToProcessProjectNames.get(x);
								packageName = extraToProcessPackageNames.get(x);

								Object packages4project = packagesToVisit.get(projectName);
								if (packages4project != null) listWithThisPackage = (ArrayList<String>)packages4project;
								else listWithThisPackage = new ArrayList<String>();

								listWithThisPackage.add(packageName);

								packagesToVisit.put(projectName, listWithThisPackage);
							}
						}

						try
						{
							processRoot(packagesToVisit, xmlFile, warningsFile, startPercent, endPercent, xmlFiles, warningsFiles);
						}
						catch (Exception e)
						{
							LogUtil.logger().log(Level.SEVERE, "Exception while generating documentation.", e);
							exceptions.add(e);
						}
					}
				}
				else
				{
					LogUtil.logger().fine("No Servoy plugin found.");
				}
				req.progressUpdate(100);
			}

			// If autopilot is off, then just use the parameters sent in the request.
			else
			{
				IPath xmlFile = req.getOutputFile();
				IPath warningsFile = xmlFile.removeFileExtension().addFileExtension("warnings.txt");
				try
				{
					processRoot(req.getProjectsAndPackagesToDocument(), xmlFile, warningsFile, 0, 100, xmlFiles, warningsFiles);
				}
				catch (Exception e)
				{
					LogUtil.logger().log(Level.SEVERE, "Exception while generating documentation.", e);
					exceptions.add(e);
				}
			}
		}
		catch (Throwable e)
		{
			LogUtil.logger().log(Level.SEVERE, "Exception while generating documentation.", e);
			exceptions.add(e);
		}
		req.requestHandled(xmlFiles, warningsFiles, exceptions, req.cancelRequested());

		Date finalEnd = Calendar.getInstance().getTime();
		LogUtil.logger().fine("Documentation post-processing ended at " + finalEnd.toString() + ".");
	}

	/**
	 * Given a Java project and a list of packages inside the project, this method builds a documentation XML for each
	 * listed package.
	 */
	public void processRoot(Map<String, List<String>> packagesByProject, IPath xmlFile, IPath warningsFile, int startPercent, int endPercent,
		List<IPath> xmlFiles, List<IPath> warningsFiles) throws Exception
	{
		JavadocExtractor javadocExtractorVisitor = new JavadocExtractor();

		if (LogUtil.logger().isLoggable(Level.FINE))
		{
			StringBuffer sb = new StringBuffer();
			sb.append("Processing root with ").append(packagesByProject.size()).append(" projects:\n");
			for (String prjName : packagesByProject.keySet())
			{
				List<String> packagesNames = packagesByProject.get(prjName);
				sb.append(prjName).append(":");
				for (String packName : packagesNames)
					sb.append(" ").append(packName);
				sb.append("\n");
			}
			LogUtil.logger().fine(sb.toString());
		}
		int total = 0;
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		LogUtil.logger().fine("Workspace root is '" + root.getLocation().toPortableString() + "'.");
		IProject[] projects = root.getProjects();
		LogUtil.logger().fine("There are " + projects.length + " projects in the workspace.");
		LogUtil.logger().fine("Counting the total number of compilation units.");
		List<IPackageFragment> selectedPackages = new ArrayList<IPackageFragment>();
		for (int i = 0; i < projects.length && !req.cancelRequested(); i++)
		{
			IProject prj = projects[i];
			if (prj.isOpen() && prj.isNatureEnabled("org.eclipse.jdt.core.javanature"))
			{
				if (packagesByProject.containsKey(prj.getName()))
				{
					List<String> packages = packagesByProject.get(prj.getName());
					IJavaProject jPrj = JavaCore.create(prj);
					int prjFiles = 0;
					IPackageFragment[] fragments = jPrj.getPackageFragments();
					for (int j = 0; j < fragments.length && !req.cancelRequested(); j++)
					{
						IPackageFragment pkg = fragments[j];
						if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE)
						{
							String thisPackageName = pkg.getElementName();
							String usedPrefix = null;
							// Check if the package is listed, or if it is a subpackage of a listed package.
							boolean process = false;
							for (String prefix : packages)
							{

								if (matchesPackage(thisPackageName, prefix))
								{
									process = true;
									usedPrefix = prefix;
									break;
								}
							}
							if (process)
							{
								// Select this package for later processing.
								int pkgFiles = pkg.getCompilationUnits().length;
								selectedPackages.add(pkg);
								prjFiles += pkgFiles;
								LogUtil.logger().fine("Package '" + pkg.getElementName() + "' in project '" + prj.getName() + "' will be documented with " +
									pkgFiles + " compilation units. The prefix used was '" + usedPrefix + "'.");
							}
							else
							{
								LogUtil.logger().fine("Skipping package '" + pkg.getElementName() + "' in project '" + prj.getName() +
									"' because it was not listed among the packages to document.");
							}
						}
					}
					LogUtil.logger().fine("Project '" + prj.getName() + "' will be documented with " + prjFiles + " compilation units.");
					total += prjFiles;
				}
				else
				{
					LogUtil.logger().fine("Skipping project '" + prj.getName() + "' because it is not listed among the projects to document.");
				}
			}
			else
			{
				LogUtil.logger().fine("Skipping roject '" + prj.getName() + "' because it is not a Java project.");
			}
		}
		LogUtil.logger().fine("Total number of compilation units to parse is " + total + ".");

		int lastPercent = startPercent;
		int reserve = 2; // 2% for writing the XML
		int visited = 0;
		req.progressUpdate(startPercent);

		// Parse each compilation unit from the selected packages.
		for (int i = 0; i < selectedPackages.size() && !req.cancelRequested(); i++)
		{
			IPackageFragment pkg = selectedPackages.get(i);
			LogUtil.logger().fine("Parsing compilation units from package '" + pkg.getElementName() + "'.");
			for (int k = 0; k < pkg.getCompilationUnits().length && !req.cancelRequested(); k++)
			{
				ICompilationUnit comp = pkg.getCompilationUnits()[k];

				// We must reconfigure the parser each time, because createAST clears all settings.
				parser.setResolveBindings(true);
				parser.setIgnoreMethodBodies(true);
				parser.setProject(pkg.getJavaProject());
				parser.setSource(comp);

				String allCode = comp.getSource();
				if (allCode != null)
				{
					SourceCodeTracker tracker = new SourceCodeTracker(allCode);
					javadocExtractorVisitor.setSourceCodeTracker(tracker);
				}
				else
				{
					javadocExtractorVisitor.setSourceCodeTracker(null);
				}

				// Create the AST and send it to the visitor.
				ASTNode cu = parser.createAST(null);
				cu.accept(javadocExtractorVisitor);

				// Report progress. Make sure we don't output the same percentage more than once.
				visited += 1;
				int newPercent = startPercent + (endPercent - startPercent - reserve) * visited / total;
				if (newPercent > lastPercent)
				{
					req.progressUpdate(newPercent);
					lastPercent = newPercent;
				}
			}
		}

		if (!req.cancelRequested())
		{
			// Generate the documentation XML and the warnings file.
			MetaModelHolder holder = javadocExtractorVisitor.getRawDataHolder();

			Set<DocumentationWarning> allWarnings = new TreeSet<DocumentationWarning>();

			IDocumentationGenerator docgen;
			if (req.getDocumentationGeneratorID() != null)
			{
				docgen = Activator.getDefault().getGenerator(req.getDocumentationGeneratorID());
				if (docgen == null)
				{
					LogUtil.logger().severe("Cannot find doc generator with ID '" + req.getDocumentationGeneratorID() + "'.");
				}
				else
				{
					LogUtil.logger().info("Using doc generator with ID '" + req.getDocumentationGeneratorID() + "'.");
				}
			}
			else
			{
				docgen = new DefaultDocumentationGenerator();
				LogUtil.logger().info("Using default doc generator.");
			}

			if (docgen != null)
			{
				InputStream xmlStream = docgen.generate(req, holder, allWarnings, xmlFile);
				if (xmlStream != null)
				{
					if (writeFile(root, xmlFile, xmlStream))
					{
						xmlFiles.add(xmlFile);
					}
				}
				req.progressUpdate(endPercent - 1);

				if (!req.cancelRequested())
				{
					// If there are warnings, report them.
					if (allWarnings.size() > 0)
					{
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						PrintWriter wout = new PrintWriter(baos);
						wout.println(allWarnings.size() + " warnings");
						wout.println();
						for (DocumentationWarning dw : allWarnings)
						{
							wout.println(dw.toString());
						}
						wout.close();
						baos.close();
						ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
						if (writeFile(root, warningsFile, bais))
						{
							warningsFiles.add(warningsFile);
						}
						LogUtil.logger().fine("Warnings written to file '" + warningsFile.toOSString() + "'.");
					}
					// If there is no warning, empty the warnings file if it already exists,
					// otherwise don't create it.
					else
					{
						IFile f = root.getFile(warningsFile);
						if (f.exists())
						{
							f.setContents((InputStream)null, true, true, null);
						}
					}
					req.progressUpdate(endPercent);
				}
			}
		}
	}

	/**
	 * @param thisPackageName
	 * @param prefix
	 * @return
	 */
	private boolean matchesPackage(String packageName, String prefix)
	{
		return packageName.equals(prefix) || packageName.startsWith(prefix + '.');
	}

	private boolean writeFile(IWorkspaceRoot root, IPath path, InputStream content) throws CoreException
	{
		IFile f = root.getFile(path);
		if (f.exists())
		{
			if (!req.confirmResourceOverwrite(path))
			{
				return false;
			}
			else
			{
				f.setContents(content, true, true, null);
			}
		}
		else
		{
			f.create(content, true, null);
		}
		return true;
	}

	/**
	 * Import existing projects into the workspace and open closed projects.
	 * @param sourceFolder
	 * @param workspaceRoot
	 * @param importedProjects
	 * @param existingClosedProjects
	 * @throws CoreException
	 */
	private void importExistingAndOpenClosedProjects(File sourceFolder, List<IProject> importedProjects, List<IProject> existingClosedProjects)
	{
		try
		{
			IWorkspaceDescription desc = workspaceRoot.getWorkspace().getDescription();
			desc.setAutoBuilding(false);
			workspaceRoot.getWorkspace().setDescription(desc);
		}
		catch (CoreException e)
		{
			LogUtil.logger().log(Level.SEVERE, "Cannot disable auto build: " + e.getMessage());
		}

		boolean useLinks = !workspaceRoot.getLocation().toFile().equals(sourceFolder);

		for (File f : sourceFolder.listFiles())
		{
			// this assumes that the name defined in ".project" matches the name of the parent folder;
			// if needed in the future, Workspace.loadProjectDescription(<.project>) can be used before we create the Project instance

			IProject p = workspaceRoot.getProject(f.getName());
			if (f.isDirectory() && new File(f, ".project").exists())
			{
				if (!p.exists() || !p.isOpen())
				{
					try
					{
						boolean existed = p.exists();
						if (!existed)
						{
							if (useLinks)
							{
								// create a new project in this workspace linking to the real project location
								IProjectDescription projectDescription = workspaceRoot.getWorkspace().newProjectDescription(p.getName());
								projectDescription.setLocationURI(f.toURI());
								p.create(projectDescription, null);
							}
							else
							{
								// real project location is default - directly inside workspace folder
								p.create(null);
							}

							importedProjects.add(p);
						}
						if (!p.isOpen())
						{
							p.open(null);

							if (existed)
							{
								if (!p.getLocation().toFile().equals(f))
								{
									LogUtil.logger().log(Level.SEVERE,
										"Cannot use project in alternate location '" + f.getAbsolutePath() +
											"'. Another project with that name is already present in workspace from location '" +
											p.getLocation().toFile().getAbsolutePath() + "'.");
								}

								// if a previous export operation managed to create the project but failed to open it, subsequent exports
								// should try to temporarily open it again (useful for automatic build systems where it would be hard to know why the projects are not used anymore otherwise)
								existingClosedProjects.add(p);
							}

						}
					}
					catch (Exception e)
					{
						LogUtil.logger().log(Level.SEVERE, "Cannot import and open project '" + f.getName() + "' into workspace. Check workspace log.");
					}
				}
				else if (useLinks && !p.getLocation().toFile().equals(f))
				{
					LogUtil.logger().log(Level.SEVERE,
						"Cannot use project in alternate location '" + f.getAbsolutePath() +
							"'. Another project with that name is already present in workspace from location '" + p.getLocation().toFile().getAbsolutePath() +
							"'.");
				}
			}
		}
		try
		{
			workspaceRoot.getWorkspace().save(true, null);
		}
		catch (CoreException e)
		{
			LogUtil.logger().log(Level.SEVERE, "Could not save workspace " + e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Restore the workspace: remove imported projects and close projects opened by importExistingAndOpenClosedProjects.
	 * @param importedProjects
	 * @param existingClosedProjects
	 */
	private void restoreImportedAndClosedProjects(List<IProject> importedProjects, List<IProject> existingClosedProjects)
	{
		LogUtil.logger().log(Level.INFO, "Restoring closed projects if needed.");
		for (IProject p : existingClosedProjects)
		{
			try
			{
				p.close(null);
			}
			catch (CoreException e)
			{
				LogUtil.logger().log(Level.WARNING, "Cannot restore project '" + p.getName() + "' to it's closed state after export. Check workspace log.");
			}
		}
		LogUtil.logger().log(Level.INFO, "Removing imported projects from workspace (without removing content) if needed.");
		for (IProject p : importedProjects)
		{
			try
			{
				p.delete(false, true, null);
			}
			catch (CoreException e)
			{
				LogUtil.logger().log(Level.WARNING,
					"Cannot remove project (not content) '" + p.getName() + "' from workspace after export. Check workspace log.");
			}
		}
	}

	private void refreshProjects()
	{
		IProject[] prjs = workspaceRoot.getProjects();
		try
		{
			for (IProject p : prjs)
			{
				if (p.isOpen() && p.exists())
				{
					p.refreshLocal(IResource.DEPTH_INFINITE, null);
				}
			}

//			ITargetPlatformService service = (ITargetPlatformService)PDECore.getDefault().acquireService(ITargetPlatformService.class.getName());
//			ITargetDefinition target = service.getTarget(workspaceRoot.getFile(new Path(SERVOY_TARGET))).getTargetDefinition();
//			target.resolve(new NullProgressMonitor());
//			LoadTargetDefinitionJob.load(target);
			workspaceRoot.getWorkspace().build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
			workspaceRoot.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
		}
		catch (CoreException e)
		{
			LogUtil.logger().log(Level.WARNING, "Refresh project roots encountered a problem. Check workspace log.");
		}
	}
}
