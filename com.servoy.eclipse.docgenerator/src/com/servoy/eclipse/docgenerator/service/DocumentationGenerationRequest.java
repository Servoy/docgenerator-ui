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

package com.servoy.eclipse.docgenerator.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IPath;

import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;

/**
 * Clients that want some documentation to be generated should register an instance of this class
 * as a service in the OSGi framework. The documentation generator listens to such services and
 * handles them when they get registered into OSGi.
 * 
 * @author gerzse
 */
public interface DocumentationGenerationRequest
{
	/**
	 * Lists the Java projects and packages that should be documented.
	 * The names of the projects are the keys of the map and for each
	 * project name the associated value is the list of packages.
	 */
	Map<String, List<String>> getProjectsAndPackagesToDocument();

	/**
	 * If needed, only certain categories of documented object can be processed.
	 * The desired categories should be returned as a set of strings. 
	 * 
	 * If null is returned, then all categories are processed.
	 */
	Set<String> getCategoryFilter();

	/**
	 * If this method returns true, then the documentation generator scans
	 * the indicated projects and packages and looks for classes that implement
	 * com.servoy.j2db.plugins.IClientPlugin. For each such class that is found
	 * a separate documentation XML is generated for the package that holds the
	 * class and all its subpackages.
	 */
	boolean autopilot();

	/**
	 * If this method returns true, then, whenever a type that is not marked with
	 * @ServoyDocumented shows up as parameter or return type of methods, an attempt
	 * will be done to map the type to a subclass that is annotated.
	 */
	boolean tryToMapUndocumentedTypes();

	/**
	 * The file where to put the generated documentation. The content
	 * of the file will be XML.
	 * 
	 * This method should return null when autopilot() returns true, because
	 * in that case more than one XML will be generated and the documentation
	 * generator will choose their paths.
	 */
	IPath getOutputFile();

	/**
	 * The ID of the documentation generator that should be used to generate the XML.
	 * If null, the default documentation generator is used. If a documentation
	 * generator is contributed via the extension point, its ID can be specified here.
	 */
	String getDocumentationGeneratorID();

	/**
	 * If the documentation XML file already exists on disk, then this method
	 * will be invoked to acknowledge overwriting the file. If the method
	 * returns true, then the file will be overwritten. If it returns false
	 * then the file will not be overwritten (the documentation will not be
	 * written to disk).
	 * 
	 * This method is invoked in the same way for the file that holds the
	 * warnings related to the Javadocs.
	 */
	boolean confirmResourceOverwrite(IPath path);

	/**
	 * Notification about the overall progress of the operation.
	 * 
	 * @param percentDone The amount of work that was performed (percentage).
	 */
	void progressUpdate(int percentDone);

	/**
	 * Gives the opportunity to perform any desired post processing before 
	 * the documentation is written to the XML file.
	 * 
	 * @param docs The documentation as it was gathered from the Javadocs. Any needed modification
	 * 				can be performed upon it, for example renaming types, replacing content, etc.
	 * @param actualOutputFile The actual output path where the data will be written.
	 */
	void postProcess(MetaModelHolder docs, IPath actualOutputFile);

	/**
	 * Notification sent when the documentation generation has ended.
	 * The generated files are sent as parameters (a list with the names of XML files 
	 * and a list with the names of the text files that hold the warnings). 
	 * Also if any exceptions are encountered, they are sent in a list (if 
	 * everything went fine, the list will be null or empty). If there was any exception, 
	 * then the two lists of files may be null or empty. 
	 * 
	 * The fourth parameter will be set to true if the request was canceled by the user.
	 */
	void requestHandled(List<IPath> xmlFiles, List<IPath> warningsFiles, List<Throwable> exceptions, boolean canceled);

	/**
	 * This method is invoked repeatedly by the documentation generator during the 
	 * documentation generation process. If the method returns true, then the generator 
	 * engine will try its best to stop the documentation generation process.
	 */
	boolean cancelRequested();
}
