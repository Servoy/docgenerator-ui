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
import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.service.DocumentationGenerationRequest;
import com.servoy.eclipse.docgenerator.service.LogUtil;

/**
 * Does some basic sanity check on JavaScript sample code.
 * Also generates an overview with all unique sample code, in HTML format, for easier visual inspection.
 *
 * @author gerzse
 */

public class SampleCodeAnalyzer
{
	private final MetaModelHolder holder;
	private final DocumentationGenerationRequest req;
	private final IPath path;
	private boolean odd = true;

	public SampleCodeAnalyzer(MetaModelHolder holder, DocumentationGenerationRequest req, IPath path)
	{
		this.holder = holder;
		this.req = req;
		this.path = path;
	}

	public void analyzeAndReport()
	{
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			listAndCheckJS(baos);
			baos.close();

			ByteArrayInputStream content = new ByteArrayInputStream(baos.toByteArray());
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IWorkspaceRoot root = workspace.getRoot();
			LogUtil.logger().log(Level.INFO, "Used workspace: " + root.getRawLocation().toOSString());
			IFile f = root.getFile(path);
			LogUtil.logger().log(Level.INFO, "Using file: " + f.getFullPath() + "," + f.getLocation() + "," + f.getWorkspace());
			if (f.exists())
			{
				if (req.confirmResourceOverwrite(path))
				{
					f.setContents(content, true, true, null);
				}
			}
			else
			{
				f.create(content, true, null);
			}
		}
		catch (Exception e)
		{
			LogUtil.logger().log(Level.SEVERE, "Exception while analyzing sample code.", e);
		}
	}

	private void listAndCheckJS(ByteArrayOutputStream baos)
	{
		try
		{
			Set<DocumentationWarning> warnings = new TreeSet<DocumentationWarning>();
			PrintWriter out = new PrintWriter(baos);
			out.println("<html><body>");
			for (TypeMetaModel typeMM : holder.getSortedTypes())
			{
				for (IMemberMetaModel memberMM : typeMM.getMembers(holder))
				{
					JavadocMetaModel docMM = memberMM.getJavadoc(holder);
					if (docMM != null)
					{
						String sampleCode = ExtractorUtil.grabExactlyOne(DocumentationDataDistilled.TAG_SAMPLE, true, docMM, warnings,
							memberMM.getFullSignature());
						if (sampleCode != null)
						{
							sampleCode = sampleCode.replaceAll("%%elementName%%", "elementName");
							sampleCode = sampleCode.replaceAll("%%prefix%%", "prefix.");
							sampleCode = sampleCode.replaceAll("&#47;", "/");
							out.println("<div style=\"background-color: #" + (odd ? "DDDDDD" : "CCCCCC") + "\">");
							out.println("<h2>" + memberMM.getFullSignature() + "</h2>");
							out.println(
								"<pre style=\"margin-left: 30px; margin-right: 30px; border: 1px solid black; background-color: white; color: #111111;\">" +
									sampleCode.replaceAll("<", "&lt;") + "\n</pre>");
							out.println("<br/>");
							validateJS(sampleCode, out);
							out.println("</div>");
							odd = !odd;
						}
					}
				}
			}
			out.println("</body></html>");
			out.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void validateJS(String code, final PrintWriter out)
	{
		try
		{
			Parser jsParser = new Parser();
			AstRoot ast = jsParser.parse(code, null, 0);
			out.println("<font color=\"green\"><b>SYNTAX OK</b></font><br/><br/>");
//			out.println("<b>Function calls:</b><br/>");
//			ast.visit(new NodeVisitor()
//			{
//				public boolean visit(AstNode node)
//				{
//					if (node instanceof FunctionCall)
//					{
//						FunctionCall fc = (FunctionCall)node;
//						out.println("<code>" + fc.toSource().trim() + "</code><br/>");
//					}
//					return true;
//				}
//			});
//			out.println("<br/>");
			out.flush();
		}
		catch (EvaluatorException ee)
		{
			out.println("<font color=\"red\"><b>PARSE ERROR: " + ee.getMessage() + "</b></font><br/>");
		}
		catch (Exception e)
		{
			out.println("Exception: <pre>");
			e.printStackTrace(out);
			out.println("</pre><br/>");
			out.flush();
			e.printStackTrace();
		}
	}

}
