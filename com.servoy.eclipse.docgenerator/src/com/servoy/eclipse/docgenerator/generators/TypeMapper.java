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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.print.PrinterJob;
import java.lang.reflect.Array;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;

/**
 * Performs type mapping. We don't want to expose certain types, and we want 
 * to map standard types to a set of classes which represent JavaScript types
 * and have Javadocs.
 * 
 * @author gerzse
 */
@SuppressWarnings("nls")
public class TypeMapper
{
	public static final String DOCS_PACKAGE = "com.servoy.j2db.documentation.scripting.docs.";

	private static Map<String, TypeName> typesMapping = new HashMap<String, TypeName>();

	private final boolean mapUndocumentedTypes;

	public TypeMapper(boolean mapUndocumentedTypes)
	{
		// -------------------------------- IMPORTANT -------------------------------------
		// if you CHANGE something in these mappings you probably need to update JavaToDocumentedJSTypeTranslator's map as well

		this.mapUndocumentedTypes = mapUndocumentedTypes;

		// Create some default mappings for standard types.
		store(Boolean.class, Boolean.class.getSimpleName());
		store(boolean.class, Boolean.class.getSimpleName());

		store(Double.class, Number.class.getSimpleName());
		store(double.class, Number.class.getSimpleName());
		store(Float.class, Number.class.getSimpleName());
		store(float.class, Number.class.getSimpleName());
		store(Long.class, Number.class.getSimpleName());
		store(long.class, Number.class.getSimpleName());
		store(Integer.class, Number.class.getSimpleName());
		store(int.class, Number.class.getSimpleName());
		store(Byte.class, "byte");
		store(byte.class, "byte");
		store(Short.class, Number.class.getSimpleName());
		store(short.class, Number.class.getSimpleName());
		store(Number.class, Number.class.getSimpleName());

		store(Date.class, Date.class.getSimpleName());
		store(java.sql.Date.class, Date.class.getSimpleName());

		store(Character.class, String.class.getSimpleName());
		store(char.class, String.class.getSimpleName());
		store(String.class, String.class.getSimpleName());
		store(Dimension.class, String.class.getSimpleName());
		store(Insets.class, String.class.getSimpleName());
		store(Point.class, String.class.getSimpleName());
		store(Color.class, String.class.getSimpleName());

		store("org.mozilla.javascript.NativeArray", Array.class.getSimpleName());
		store("org.mozilla.javascript.NativeJavaArray", Array.class.getSimpleName());
		store("org.json.JSONArray", Array.class.getSimpleName());

		store(Object.class, Object.class.getSimpleName());
		store("org.mozilla.javascript.Scriptable", Object.class.getSimpleName());
		store("com.servoy.j2db.scripting.JSMap", Object.class.getSimpleName());

		store("org.mozilla.javascript.Function", "Function");

		store(Exception.class, "com.servoy.j2db.util.ServoyException");

		store("com.servoy.j2db.dataprocessing.IFoundSetInternal", "com.servoy.j2db.dataprocessing.FoundSet");
		store("com.servoy.j2db.dataprocessing.IFoundSet", "com.servoy.j2db.dataprocessing.FoundSet");
		store("com.servoy.j2db.dataprocessing.RelatedFoundSet", "com.servoy.j2db.dataprocessing.FoundSet");
		store("com.servoy.base.scripting.api.IJSFoundSet", "com.servoy.j2db.dataprocessing.FoundSet");

		store("com.servoy.j2db.dataprocessing.IDataSet", "com.servoy.j2db.dataprocessing.JSDataSet");
		store("com.servoy.base.scripting.api.IJSDataSet", "com.servoy.j2db.dataprocessing.JSDataSet");

		store("com.servoy.j2db.dataprocessing.IRecordInternal", "com.servoy.j2db.dataprocessing.Record");
		store("com.servoy.base.scripting.api.IJSRecord", "com.servoy.j2db.dataprocessing.Record");
		store("com.servoy.j2db.dataprocessing.IRecord", "com.servoy.j2db.dataprocessing.Record");

		store("com.servoy.j2db.ui.IComponent", "IRuntimeComponent");

		store("com.servoy.j2db.solutionmodel.ISMComponent", "com.servoy.j2db.scripting.solutionmodel.JSComponent");
		store("com.servoy.j2db.solutionmodel.ISMMethod", "com.servoy.j2db.scripting.solutionmodel.JSMethod");
		store("com.servoy.j2db.solutionmodel.ISMVariable", "com.servoy.j2db.scripting.solutionmodel.JSVariable");
		store("com.servoy.j2db.solutionmodel.ISMForm", "com.servoy.j2db.scripting.solutionmodel.JSForm");
		store("com.servoy.j2db.solutionmodel.ISMButton", "com.servoy.j2db.scripting.solutionmodel.JSButton");
		store("com.servoy.j2db.solutionmodel.ISMLabel", "com.servoy.j2db.scripting.solutionmodel.JSLabel");
		store("com.servoy.j2db.solutionmodel.ISMField", "com.servoy.j2db.scripting.solutionmodel.JSFieldWithConstants");
		store("com.servoy.j2db.solutionmodel.ISMPart", "com.servoy.j2db.scripting.solutionmodel.JSPartWithConstants");

		store("com.servoy.base.solutionmodel.IBaseSMComponent", "com.servoy.j2db.scripting.solutionmodel.JSComponent");
		store("com.servoy.base.solutionmodel.IBaseSMMethod", "com.servoy.j2db.scripting.solutionmodel.JSMethod");
		store("com.servoy.base.solutionmodel.IBaseSMVariable", "com.servoy.j2db.scripting.solutionmodel.JSVariable");
		store("com.servoy.base.solutionmodel.IBaseSMForm", "com.servoy.j2db.scripting.solutionmodel.JSForm");
		store("com.servoy.base.solutionmodel.IBaseSMButton", "com.servoy.j2db.scripting.solutionmodel.JSButton");
		store("com.servoy.base.solutionmodel.IBaseSMLabel", "com.servoy.j2db.scripting.solutionmodel.JSLabel");
		store("com.servoy.base.solutionmodel.IBaseSMField", "com.servoy.j2db.scripting.solutionmodel.JSFieldWithConstants");
		store("com.servoy.base.solutionmodel.IBaseSMPart", "com.servoy.j2db.scripting.solutionmodel.JSPartWithConstants");

		store("com.servoy.j2db.scripting.solutionmodel.JSPart", "com.servoy.j2db.scripting.solutionmodel.JSPartWithConstants");
		store("com.servoy.j2db.scripting.solutionmodel.JSField", "com.servoy.j2db.scripting.solutionmodel.JSFieldWithConstants");

		store("com.servoy.base.solutionmodel.mobile.IMobileSMForm", "com.servoy.j2db.scripting.solutionmodel.JSForm");
		store("com.servoy.base.solutionmodel.mobile.IMobileSMLabel", "com.servoy.j2db.scripting.solutionmodel.JSLabel");

		store("com.servoy.j2db.IForm", "Form");
		store("com.servoy.j2db.scripting.FormScope", "Form");
		store("com.servoy.base.scripting.api.IJSController", "Form");

		store("com.servoy.j2db.querybuilder.IQueryBuilderCondition", "com.servoy.j2db.querybuilder.impl.QBCondition");
		store("com.servoy.j2db.querybuilder.IQueryBuilderLogicalCondition", "com.servoy.j2db.querybuilder.impl.QBLogicalCondition");
		store("com.servoy.j2db.querybuilder.IQueryBuilderWhereCondition", "com.servoy.j2db.querybuilder.impl.QBWhereCondition");

		store("com.servoy.j2db.ui.IScriptRenderMethods", "com.servoy.j2db.ui.IScriptRenderMethodsWithFormat");

		store(PrinterJob.class, PrinterJob.class.getSimpleName());

		store("com.servoy.extensions.plugins.window.menu.AbstractMenuItem", "com.servoy.extensions.plugins.window.menu.MenuItem");

		storeSimple(void.class);
	}

	private void store(Class< ? > cls, String target)
	{
		store(cls.getCanonicalName(), target);
//		store(cls.getSimpleName(), target);
	}

	private void store(String cls, String target)
	{
		String prefix = null;
		if (target.indexOf(".") == -1) prefix = DOCS_PACKAGE;
		TypeName tn = new TypeName(target, prefix);
		typesMapping.put(cls, tn);
	}

	private void storeSimple(Class< ? > cls)
	{
		TypeName tn = new TypeName(cls.getSimpleName(), null);
		typesMapping.put(cls.getCanonicalName(), tn);
	}

	/**
	 * Try to map a type to a (possibly different) public type.
	 *
	 * If a mapping is found, true is written into the boolean array on the first position. Otherwise false is written.
	 */
	public TypeName mapType(MetaModelHolder docs, TypeName originalType, boolean partialMatch, boolean[] wasFound)
	{
		// null type remains unmapped
		if (originalType == null)
		{
			wasFound[0] = false;
			return null;
		}

		wasFound[0] = true;
		// If the type is already public, then keep it.
		String baseName = originalType.getBaseName();
		if (docs.hasType(baseName))
		{
			TypeMetaModel existing = docs.getType(baseName);
			if (existing.isServoyDocumented())
			{
				return originalType;
			}
		}

		// special case for byte[]
		if (originalType.getQualifiedName().equals(byte[].class.getSimpleName()))
		{
			return originalType;
		}

		// if the custom mapping table contains our type, then use it
		// to do the mapping
		if (typesMapping.containsKey(baseName))
		{
			TypeName ref = typesMapping.get(baseName);
			return originalType.changeBaseTo(ref);
		}

		if (partialMatch)
		{
			// Try a search based on short names.
			for (TypeMetaModel typeMM : docs.getTypes())
			{
				String typeShortName = typeMM.getName().getShortName().replaceAll("\\[\\]", "");
				if (typeShortName.equals(originalType.getShortName()))
				{
					return typeMM.getName();
				}
			}

			// Try a search based on public names
			for (TypeMetaModel typeMM : docs.getTypes())
			{
				if (typeMM.getPublicName().equals(originalType.getShortName()))
				{
					return typeMM.getName();
				}
			}
		}

		if (mapUndocumentedTypes)
		{
			// if nothing succeeded so far, try to find a class
			// that implements our type
			TypeName equiv = null;
			for (TypeMetaModel cdr : docs.getTypes())
			{
				if (cdr.isServoyDocumented())
				{
					if (cdr.getSupertype() != null && originalType.isSameType(cdr.getSupertype()))
					{
						equiv = cdr.getName();
						break;
					}
					boolean found = false;
					if (cdr.getInterfaces() != null)
					{
						for (TypeName i : cdr.getInterfaces())
						{
							if (originalType.isSameType(i))
							{
								found = true;
								equiv = cdr.getName();
								break;
							}
						}
					}
					if (found)
					{
						break;
					}
				}
			}
			if (equiv != null)
			{
				typesMapping.put(originalType.getBaseName(), equiv);
				return originalType.changeBaseTo(equiv);
			}
		}

		// if still nothing was found, just return the original
		wasFound[0] = false;
		return originalType;
	}
}
