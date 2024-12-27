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

import java.util.LinkedHashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.servoy.eclipse.docgenerator.metamodel.ClientSupport;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel.Visibility;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.MethodMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;
import com.servoy.eclipse.docgenerator.util.Pair;

/**
 * @author gerzse
 */
public class MethodStoragePlace extends MemberStoragePlace
{

	public static final String ARRAY_INDEX_PROPERTY_PREFIX = "array__indexedby_";
	// for methods
	private static final String TAG_PARAMETER = "parameter";
	private static final String TAG_PARAMETERS = "parameters";
	private static final String TAG_ARGUMENT_TYPE = "argumentType";
	private static final String TAG_ARGUMENTS_TYPES = "argumentsTypes";
	private static final String ATTR_VARARGS = "varargs";

	private static final String ANNOTATION_JS_READONLY_PROPERTY = "JSReadonlyProperty";
	private static final String ANNOTATION_JS_GETTER = "JSGetter";
	private static final String ANNOTATION_JS_FUNCTION = "JSFunction";

	private static final String JS_PREFIX = "js_";
	private static final String JS_FUNCTION_PREFIX = "jsFunction_";
	private static final String JS_CONSTRUCTOR_PREFIX = "jsConstructor_";

	private final String officialName;

	private final LinkedHashMap<String, TypeName> parameters = new LinkedHashMap<String, TypeName>();

	public MethodStoragePlace(MethodMetaModel methodMM, TypeMetaModel typeMM, MetaModelHolder holder)
	{
		super(methodMM, typeMM, holder);
		officialName = buildOfficialName();
	}

	private MethodMetaModel getMethodMM()
	{
		return (MethodMetaModel)memberMM;
	}

	@Override
	public String getOfficialName()
	{
		return officialName;
	}

	@Override
	public String getOfficialSignature()
	{
		int paren = getMethodMM().getIndexSignature().indexOf("(");
		return getOfficialName() + getMethodMM().getIndexSignature().substring(paren);
	}

	@Override
	public String getKind()
	{
		if (getMethodMM().getName().startsWith(JS_CONSTRUCTOR_PREFIX))
		{
			return DefaultDocumentationGenerator.TAG_CONSTRUCTOR;
		}

		boolean isProperty = false;
		if (holder.getAnnotationManager().hasAnnotation(getMethodMM(), typeMM, ANNOTATION_JS_READONLY_PROPERTY) ||
			holder.getAnnotationManager().hasAnnotation(getMethodMM(), typeMM, ANNOTATION_JS_GETTER))
		{
			isProperty = true;
		}
		else if (holder.getAnnotationManager().hasAnnotation(getMethodMM(), typeMM, ANNOTATION_JS_FUNCTION))
		{
			isProperty = false;
		}
		else
		{
			String shortName = getMethodMM().getName();
			boolean wasPrefixed = false;
			if (shortName.startsWith(JS_PREFIX))
			{
				wasPrefixed = true;
				shortName = shortName.substring(JS_PREFIX.length());
			}
			String goodPref = null;
			if (shortName.startsWith("get"))
			{
				goodPref = "get";
			}
			else if (shortName.startsWith("is"))
			{
				goodPref = "is";
			}
			if (goodPref != null && getMethodMM().getType() != null)
			{
				String propName = shortName.substring(goodPref.length());
				if (hasSetter(propName, getMethodMM().getType(), wasPrefixed) || //
					hasSetter(propName, new TypeName(Object.class), wasPrefixed) // special case when the setter has an Object parameter
				)
				{
					isProperty = true;
				}
			}
		}
		return isProperty ? DefaultDocumentationGenerator.TAG_PROPERTY : DefaultDocumentationGenerator.TAG_FUNCTION;
	}

	public LinkedHashMap<String, TypeName> getParameters()
	{
		return parameters;
	}

	@Override
	public Element toXML(TypeMetaModel tmm, Document doc, boolean includeSample, ClientSupport scp)
	{
		Element root = super.toXML(tmm, doc, includeSample, scp);
		if (getMethodMM().isVarargs())
		{
			root.setAttribute(ATTR_VARARGS, Boolean.TRUE.toString());
		}
		DocumentationDataDistilled ddr = getDocDataRecursively(tmm);
		if (!hideParameters())
		{
			Element argTypes = doc.createElement(TAG_ARGUMENTS_TYPES);
			for (String parName : getParameters().keySet())
			{
				TypeName parType = getParameters().get(parName);
				Element argType = doc.createElement(TAG_ARGUMENT_TYPE);
				argTypes.appendChild(argType);
				argType.setAttribute(DefaultDocumentationGenerator.ATTR_TYPECODE, parType.getBinaryName());
			}
			root.insertBefore(argTypes, root.getFirstChild());
		}
		boolean showParameters = false;
		if (ddr != null && ddr.hasDocumentation())
		{
			showParameters = ddr.getParameters().size() > 0;
		}
		else
		{
			showParameters = getParameters().size() > 0;
		}
		if (showParameters)
		{
			Element paramDocs = doc.createElement(TAG_PARAMETERS);
			NodeList existing = root.getChildNodes();
			boolean was = false;
			Node place = null;
			// try to place the parameters right after the sample
			for (int i = 0; i < existing.getLength(); i++)
			{
				Node ex = existing.item(i);
				if (was)
				{
					place = ex;
					break;
				}
				if ("sample".equals(ex.getNodeName()))
				{
					was = true;
				}
			}
			if (place == null)
			{
				root.appendChild(paramDocs);
			}
			else
			{
				root.insertBefore(paramDocs, place);
			}
			if (ddr != null && ddr.hasDocumentation())
			{
				for (DocumentedParameterData par : ddr.getParameters())
				{
					String parName = par.getName();
					Element elPar = par.toXML(doc);
					TypeName parType = null;
					// If the parameter name is from the method signature, then grab the real type.
					if (getParameters().containsKey(parName))
					{
						parType = getParameters().get(parName);
					}
					// Otherwise try to grab the type suggested by the user (if any and if valid).
					else if (par.getType() != null)
					{
						parType = par.getType();
					}
					if (parType != null)
					{
						elPar.setAttribute(DefaultDocumentationGenerator.ATTR_TYPE, parType.getQualifiedName());
						elPar.setAttribute(DefaultDocumentationGenerator.ATTR_TYPECODE, parType.getBinaryName());
					}
					if (par.getJSType() != null)
					{
						elPar.setAttribute(DefaultDocumentationGenerator.ATTR_JS_TYPE, par.getJSType());
					}
					paramDocs.appendChild(elPar);
				}
			}
			// for undocumented functions we still list the parameters and their types
			else
			{
				for (String parName : getParameters().keySet())
				{
					TypeName parType = getParameters().get(parName);
					Element elPar = doc.createElement(TAG_PARAMETER);
					elPar.setAttribute(ATTR_NAME, parName);
					elPar.setAttribute(DefaultDocumentationGenerator.ATTR_TYPE, parType.getQualifiedName());
					elPar.setAttribute(DefaultDocumentationGenerator.ATTR_TYPECODE, parType.getBinaryName());
					paramDocs.appendChild(elPar);
				}
			}
		}
		return root;
	}

	@Override
	public void mapTypes(TypeMapper proc)
	{
		super.mapTypes(proc);
		for (String parName : getMethodMM().getParameters().keySet())
		{
			TypeName parData = getMethodMM().getParameters().get(parName);
			boolean[] flag = new boolean[1];
			TypeName nw = proc.mapType(holder, parData, false, flag);
			if (flag[0])
			{
				getParameters().put(parName, nw);
			}
			else
			{
				getParameters().put(parName, parData);
			}
		}
	}

	@Override
	public Pair<Boolean, ClientSupport> shouldShow(TypeMetaModel realTypeMM)
	{
		// if it's private, don't show
		if (getMethodMM().getVisibility() == Visibility.Private)
		{
			return new Pair<Boolean, ClientSupport>(Boolean.FALSE, null);
		}
		// if it's static, don't show
		if (getMethodMM().isStatic())
		{
			return new Pair<Boolean, ClientSupport>(Boolean.FALSE, null);
		}

		// if it's annotated properly, then it should show
		if (holder.getAnnotationManager().hasAnnotation(getMethodMM(), realTypeMM, ANNOTATION_JS_FUNCTION) ||
			holder.getAnnotationManager().hasAnnotation(getMethodMM(), realTypeMM, ANNOTATION_JS_READONLY_PROPERTY) ||
			holder.getAnnotationManager().hasAnnotation(getMethodMM(), realTypeMM, ANNOTATION_JS_GETTER))
		{
			return new Pair<Boolean, ClientSupport>(Boolean.TRUE, null);
		}

		if (getMethodMM().getName().startsWith(JS_PREFIX))
		{
			String shortName = getMethodMM().getName().substring(JS_PREFIX.length());
			// setters from properties should not show
			if (shortName.startsWith("set") && getMethodMM().getType() != null && getMethodMM().getType().getQualifiedName().equals(void.class.getSimpleName()))
			{
				if (getMethodMM().getParameters().size() == 1)
				{
					IMemberMetaModel getter = realTypeMM.getMember(JS_PREFIX + "get" + shortName.substring("set".length()) + "()", holder);
					if (getter == null)
					{
						getter = realTypeMM.getMember(JS_PREFIX + "is" + shortName.substring("set".length()) + "()", holder);
					}
					if (getter instanceof MethodMetaModel)
					{
						TypeName par = getMethodMM().getParameters().values().iterator().next();
						MethodMetaModel getterMeth = (MethodMetaModel)getter;
						if (getterMeth.getType() != null && getterMeth.getType().getQualifiedName().equals(par.getQualifiedName()))
						{
							return new Pair<Boolean, ClientSupport>(Boolean.FALSE, null);
						}
						// special case when the setter has an Object parameter
						if (par.getQualifiedName().endsWith("." + Object.class.getSimpleName()))
						{
							return new Pair<Boolean, ClientSupport>(Boolean.FALSE, null);
						}
					}
				}
			}
			return new Pair<Boolean, ClientSupport>(Boolean.TRUE, null);
		}

		return new Pair<Boolean, ClientSupport>(Boolean.valueOf(getMethodMM().getName().startsWith(JS_FUNCTION_PREFIX) ||
			getMethodMM().getName().startsWith(JS_CONSTRUCTOR_PREFIX)), null);
	}

	private boolean hideParameters()
	{
		return !(DefaultDocumentationGenerator.TAG_CONSTRUCTOR.equals(getKind()) || DefaultDocumentationGenerator.TAG_FUNCTION.equals(getKind()));
	}

	@Override
	protected boolean hideReturnType()
	{
		return DefaultDocumentationGenerator.TAG_CONSTRUCTOR.equals(getKind());
	}

	private String buildOfficialName()
	{
		String official = getMethodMM().getName();
		if (official.startsWith(JS_PREFIX) || official.startsWith(JS_FUNCTION_PREFIX))
		{
			// start from what was build in the constructor
			official = buildOfficialNameBase(getMethodMM().getName());
		}
		else if (official.startsWith(JS_CONSTRUCTOR_PREFIX))
		{
			official = official.substring(JS_CONSTRUCTOR_PREFIX.length());
		}

		String original = official;
		if (DefaultDocumentationGenerator.TAG_PROPERTY.equals(getKind()))
		{
			boolean dontCheckFurther = false;
			boolean checkForArrayIndexProperty = false;
			if (official.startsWith("get"))
			{
				official = official.substring("get".length());
				checkForArrayIndexProperty = true;
			}
			else if (official.startsWith("is"))
			{
				official = official.substring("is".length());
			}
			else
			{
				checkForArrayIndexProperty = true; // also check annotation-marked properties... (so that you can have read-only array use)
			}

			if (checkForArrayIndexProperty)
			{
				if (official.toLowerCase().startsWith(ARRAY_INDEX_PROPERTY_PREFIX)) // see also TypeCreator.ARRAY_INDEX_PROPERTY_PREFIX which serves the same purpose
				{
					official = official.substring(ARRAY_INDEX_PROPERTY_PREFIX.length());
					// commented out because the return type should not be the index type, but the actual type of the array elements (for code completion)
//					if (getMethodMM().getType() != null && getMethodMM().getType().getShortName().equals(String.class.getSimpleName()))
//					{
//						official = "['" + official + "']";
//					}
//					else
					official = "[" + official + "]";
					dontCheckFurther = true;
				}
			}

			if (!dontCheckFurther)
			{
				if (official.startsWith("samecase_"))
				{
					official = official.substring("samecase_".length());
				}
				else if (!official.equals(official.toUpperCase()) || official.length() == 1)
				{
					official = official.substring(0, 1).toLowerCase() + official.substring(1);
				}
			}
			if (ReservedWordsChecker.isReserved(official))
			{
				return original;
			}
		}
		else
		{
			if (official.startsWith("flow_"))
			{
				official = official.substring("flow_".length()).replace("_", " ");
			}
		}
		return official;
	}

	/**
	 * Given a name, removes the "js_" or "jsFunction_" prefix from it, unless the resulted word is a JavaScript or Java keyword.
	 */
	private String buildOfficialNameBase(String originalName)
	{
		String official = originalName;
		if (official.startsWith(JS_PREFIX))
		{
			official = official.substring(JS_PREFIX.length());
		}
		else if (official.startsWith(JS_FUNCTION_PREFIX))
		{
			official = official.substring(JS_FUNCTION_PREFIX.length());
		}

		if (ReservedWordsChecker.isReserved(official))
		{
			return originalName;
		}
		else
		{
			return official;
		}
	}

	private boolean hasSetter(String propertyName, TypeName paramType, boolean wasPrefixed)
	{
		String setterSignature = "set" + propertyName + "(" + paramType.getQualifiedName() + ")";
		if (wasPrefixed)
		{
			setterSignature = JS_PREFIX + setterSignature;
		}
		IMemberMetaModel setterRaw = typeMM.getMember(setterSignature, holder);
		if (setterRaw instanceof MethodMetaModel)
		{
			MethodMetaModel setter = (MethodMetaModel)setterRaw;
			if (setter.getType() != null && setter.getType().getQualifiedName().equals(void.class.getSimpleName()))
			{
				return true;
			}
		}
		return false;
	}

	public MethodStoragePlace withMember(MethodMetaModel methodMetaModel)
	{
		return new MethodStoragePlace(methodMetaModel, this.typeMM, this.holder);
	}
}
