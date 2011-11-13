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

import com.servoy.eclipse.docgenerator.metamodel.FieldMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MethodMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;

/**
 * @author gerzse
 */
@SuppressWarnings("nls")
public class MemberDataRaw
{
	private DocumentationDataRaw docData;
	protected String kind;
	protected String officialName;
	private TypeName type;
	private boolean deprecated;
	private final LinkedHashMap<String, TypeName> parameters = new LinkedHashMap<String, TypeName>();

	public MemberDataRaw(MemberMetaModel memberMM, TypeMetaModel typeMM)
	{
		type = memberMM.getType();
		deprecated = memberMM.isDeprecated();

		if (memberMM.getJavadoc() != null)
		{
			docData = new DocumentationDataRaw(memberMM, typeMM);
		}

		// compute kind
		if (memberMM instanceof MethodMetaModel)
		{
			MethodMetaModel methodMM = (MethodMetaModel)memberMM;
			if (methodMM.getName().startsWith(DefaultDocumentationGenerator.JS_CONSTRUCTOR_PREFIX))
			{
				kind = DefaultDocumentationGenerator.TAG_CONSTRUCTOR;
			}
			else
			{
				boolean isProperty = false;
				if (methodMM.getAnnotations().hasAnnotation(DefaultDocumentationGenerator.ANNOTATION_JS_READONLY_PROPERTY))
				{
					isProperty = true;
				}
				else
				{
					String shortName = methodMM.getName();
					boolean wasPrefixed = false;
					if (shortName.startsWith(DefaultDocumentationGenerator.JS_PREFIX))
					{
						wasPrefixed = true;
						shortName = shortName.substring(DefaultDocumentationGenerator.JS_PREFIX.length());
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
					if (goodPref != null && methodMM.getType() != null)
					{
						String propName = shortName.substring(goodPref.length());
						if (hasSetter(propName, methodMM.getType(), typeMM, wasPrefixed))
						{
							isProperty = true;
						}
						else
						{
							TypeName objRetType = new TypeName(Object.class);
							// special case when the setter has an Object parameter				
							if (hasSetter(propName, objRetType, typeMM, wasPrefixed))
							{
								isProperty = true;
							}
						}
					}
				}
				if (isProperty)
				{
					kind = DefaultDocumentationGenerator.TAG_PROPERTY;
				}
				else
				{
					kind = DefaultDocumentationGenerator.TAG_FUNCTION;
				}
			}
		}
		else
		{
			kind = DefaultDocumentationGenerator.TAG_CONSTANT;
		}

		// official name depends on kind
		officialName = buildOfficialName(memberMM);
	}

	public TypeName getType()
	{
		return type;
	}

	public void setType(TypeName type)
	{
		this.type = type;
	}

	public DocumentationDataRaw getDocData()
	{
		return docData;
	}

	public void setDocData(DocumentationDataRaw docData)
	{
		this.docData = docData;
	}

	public boolean isDeprecated()
	{
		return deprecated;
	}

	public void setDeprecated(boolean deprecated)
	{
		this.deprecated = deprecated;
	}

	public String getKind()
	{
		return kind;
	}

	public String getOfficialName()
	{
		return officialName;
	}

	public LinkedHashMap<String, TypeName> getParameters()
	{
		return parameters;
	}

	private boolean hasSetter(String propertyName, TypeName paramType, TypeMetaModel typeMM, boolean wasPrefixed)
	{
		String setterSignature = "set" + propertyName + "(" + paramType.getQualifiedName() + ")";
		if (wasPrefixed)
		{
			setterSignature = DefaultDocumentationGenerator.JS_PREFIX + setterSignature;
		}
		if (typeMM.containsKey(setterSignature))
		{
			MemberMetaModel setterRaw = typeMM.get(setterSignature);
			if (setterRaw instanceof MethodMetaModel)
			{
				MethodMetaModel setter = (MethodMetaModel)setterRaw;
				if (setter.getType() != null && setter.getType().getQualifiedName().equals(void.class.getSimpleName()))
				{
					return true;
				}
			}
		}
		return false;
	}

	private String buildOfficialName(MemberMetaModel member)
	{
		if (member instanceof MethodMetaModel)
		{
			MethodMetaModel methodMM = (MethodMetaModel)member;
			String official = methodMM.getName();
			if (official.startsWith(DefaultDocumentationGenerator.JS_PREFIX) || official.startsWith(DefaultDocumentationGenerator.JS_FUNCTION_PREFIX))
			{
				// start from what was build in the constructor
				official = buildOfficialNameBase(methodMM.getName());
			}
			else if (official.startsWith(DefaultDocumentationGenerator.JS_CONSTRUCTOR_PREFIX))
			{
				official = official.substring(DefaultDocumentationGenerator.JS_CONSTRUCTOR_PREFIX.length());
			}

			if (DefaultDocumentationGenerator.TAG_PROPERTY.equals(kind))
			{
				boolean dontCheckFurther = false;
				if (official.startsWith("get"))
				{
					official = official.substring("get".length());
					if (official.toLowerCase().startsWith("index_"))
					{
						official = official.substring("index_".length());
						if (methodMM.getType() != null && methodMM.getType().getShortName().equals(String.class.getSimpleName()))
						{
							official = "['" + official + "']";
						}
						else
						{
							official = "[" + official + "]";
						}
						dontCheckFurther = true;
					}
				}
				else if (official.startsWith("is"))
				{
					official = official.substring("is".length());
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
		else
		{
			FieldMetaModel fieldMM = (FieldMetaModel)member;
			return fieldMM.getName();
		}
	}

	/**
	 * Given a name, removes the "js_" or "jsFunction_" prefix from it, unless the resulted word is a JavaScript or Java keyword.
	 */
	private String buildOfficialNameBase(String originalName)
	{
		String official = originalName;
		if (official.startsWith(DefaultDocumentationGenerator.JS_PREFIX))
		{
			official = official.substring(DefaultDocumentationGenerator.JS_PREFIX.length());
		}
		else if (official.startsWith(DefaultDocumentationGenerator.JS_FUNCTION_PREFIX))
		{
			official = official.substring(DefaultDocumentationGenerator.JS_FUNCTION_PREFIX.length());
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


}
