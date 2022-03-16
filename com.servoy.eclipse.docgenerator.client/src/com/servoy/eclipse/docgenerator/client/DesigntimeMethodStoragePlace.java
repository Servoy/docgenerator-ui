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

package com.servoy.eclipse.docgenerator.client;

import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;

import com.servoy.eclipse.docgenerator.generators.DefaultDocumentationGenerator;
import com.servoy.eclipse.docgenerator.generators.DocumentedParameterData;
import com.servoy.eclipse.docgenerator.generators.ExtractorUtil;
import com.servoy.eclipse.docgenerator.generators.MethodStoragePlace;
import com.servoy.eclipse.docgenerator.generators.TypeMapper;
import com.servoy.eclipse.docgenerator.metamodel.AnnotationMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.ClientSupport;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.IPublicStore;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocTagPart;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.MethodMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;
import com.servoy.eclipse.docgenerator.service.LogUtil;
import com.servoy.eclipse.docgenerator.util.Pair;
import com.servoy.j2db.persistence.ContentSpec;
import com.servoy.j2db.persistence.ContentSpec.Element;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.RepositoryHelper;
import com.servoy.j2db.persistence.StaticContentSpecLoader;

/**
 * @author gerzse
 */

public class DesigntimeMethodStoragePlace extends MethodStoragePlace
{
	private static final String ATTRIBUTE_MEMBER_KIND = "memberKind";

	public static ContentSpec cs = StaticContentSpecLoader.getContentSpec();
	private static final String[] packagesToCheck = new String[] { "java.lang." };
	private final String fullSearchName;
	private String newKind = null;
	private String newOfficialName = null;

	protected DesigntimeMethodStoragePlace(MethodMetaModel memberMM, TypeMetaModel typeMM, MetaModelHolder holder, TypeMapper typeMapper)
	{
		super(memberMM, typeMM, holder);

		fullSearchName = getOfficialName();
		Object memberKindOverride = null;
		if (DefaultDocumentationGenerator.TAG_PROPERTY.equals(getKind()))
		{
			// memberkind can be overridden with @ServoyDocumented(memberKind=..)
			AnnotationMetaModel sdoc = holder.getAnnotationManager().getAnnotation(memberMM, typeMM, IPublicStore.ANNOTATION_SERVOY_DOCUMENTED);
			if (sdoc != null)
			{
				memberKindOverride = sdoc.getAttribute(ATTRIBUTE_MEMBER_KIND);
				if (DesigntimeDocumentationGenerator.TAG_COMMAND.equals(memberKindOverride) ||
					DesigntimeDocumentationGenerator.TAG_EVENT.equals(memberKindOverride))
				{
					newKind = memberKindOverride.toString();
				}
			}

			if (memberKindOverride == null || "".equals(memberKindOverride))
			{
				String removedSuffix;
				if (getOfficialName().endsWith(DesigntimeDocumentationGenerator.COMMAND_SUFFIX))
				{
					newKind = DesigntimeDocumentationGenerator.TAG_COMMAND;
					removedSuffix = DesigntimeDocumentationGenerator.COMMAND_SUFFIX;
				}
				else if (getOfficialName().endsWith(DesigntimeDocumentationGenerator.EVENT_SUFFIX))
				{
					newKind = DesigntimeDocumentationGenerator.TAG_EVENT;
					removedSuffix = DesigntimeDocumentationGenerator.EVENT_SUFFIX;
				}
				else if (getOfficialName().endsWith(DesigntimeDocumentationGenerator.PROPERTY_SUFFIX))
				{
					removedSuffix = DesigntimeDocumentationGenerator.PROPERTY_SUFFIX;
				}
				else
				{
					removedSuffix = "";
				}
				newOfficialName = getOfficialName().substring(0, getOfficialName().length() - removedSuffix.length());
			}

			if (DesigntimeDocumentationGenerator.TAG_COMMAND.equals(newKind) || DesigntimeDocumentationGenerator.TAG_EVENT.equals(newKind))
			{
				// Clean return type for now. If there is one from method templates, that will be used.
				setType(null);

				JavadocMetaModel jdoc = memberMM.getJavadoc(holder);
				if (jdoc != null)
				{
					Set<DocumentationWarning> warnings = memberMM.getWarnings();
					boolean clean = true;

					// add return type, if any
					String templateType = ExtractorUtil.grabExactlyOne(MethodTemplatesXmlGenerator.TAG_TEMPLATE_TYPE, clean, jdoc, warnings,
						memberMM.getFullSignature());
					if (templateType != null)
					{
						templateType = cleanType(templateType);
						boolean flag[] = new boolean[1];
						TypeName tn = new TypeName(templateType, null);
						TypeName tentativeType = typeMapper.mapType(holder, tn, true, flag);
						if (flag[0])
						{
							setType(tentativeType);
						}
					}

					// add parameters from method template, if any
					List<JavadocTagPart> paramTags = jdoc.findTags(MethodTemplatesXmlGenerator.TAG_TEMPLATE_PARAM);
					for (JavadocTagPart paramTag : paramTags)
					{
						String paramText = paramTag.getAsString(clean);
						paramText = paramText.trim();

						StringTokenizer st = new StringTokenizer(paramText);
						if (st.hasMoreTokens())
						{
							String paramType = st.nextToken();
							if (st.hasMoreTokens())
							{
								String nextToken = null;
								while (st.hasMoreTokens() && "|".equals((nextToken = st.nextToken()).trim()))
								{
									paramType += "|" + st.nextToken();
								}
								String paramName = nextToken != null ? nextToken : st.nextToken();
								String paramDescription = paramText;
								int idx = paramDescription.indexOf(paramType);
								paramDescription = paramDescription.substring(idx + paramType.length());
								idx = paramDescription.indexOf(paramName);
								paramDescription = paramDescription.substring(idx + paramName.length());
								paramDescription = paramDescription.trim();
								DocumentedParameterData parData = new DocumentedParameterData(paramName, false, paramDescription);
								paramType = cleanType(paramType);
								parData.checkIfHasType(holder, typeMapper, paramType);
								if (parData.getType() == null && paramType != null)
								{
									parData.setJSType(paramType);
								}
								getDocData().addParameter(parData);
							}
							else
							{
								warnings.add(new DocumentationWarning(WarningType.Other, memberMM.getFullSignature(), "Missing name in " +
									MethodTemplatesXmlGenerator.TAG_TEMPLATE_PARAM + " tag."));
							}
						}
						else
						{
							warnings.add(new DocumentationWarning(WarningType.EmptyTag, memberMM.getFullSignature(), "Empty " +
								MethodTemplatesXmlGenerator.TAG_TEMPLATE_PARAM + " tag."));
						}
					}
				}
			}
		}
	}

	@Override
	public String getKind()
	{
		return newKind != null ? newKind : super.getKind();
	}

	@Override
	public String getOfficialName()
	{
		return newOfficialName != null ? newOfficialName : super.getOfficialName();
	}

	@Override
	public Pair<Boolean, ClientSupport> shouldShow(TypeMetaModel realTypeMM)
	{
		if (DefaultDocumentationGenerator.TAG_PROPERTY.equals(getKind()) || DesigntimeDocumentationGenerator.TAG_COMMAND.equals(getKind()) ||
			DesigntimeDocumentationGenerator.TAG_EVENT.equals(getKind()))
		{
			ClientSupport newClientSupport = ((MethodMetaModel)memberMM).getServoyClientSupport(realTypeMM, holder);
			try
			{
				int typeCode = -1;
				AnnotationMetaModel sdoc = holder.getAnnotationManager().getAnnotation(realTypeMM, IPublicStore.ANNOTATION_SERVOY_DOCUMENTED);
				if (sdoc != null)
				{
					typeCode = ((Integer)sdoc.getAttribute("typeCode")).intValue();
				}
				if (typeCode == -1)
				{
					typeCode = IRepository.UNRESOLVED_ELEMENT;
				}

				String fullName = getFullSearchName();

				String className = realTypeMM.getRealClassName();
				if (className == null)
				{
					className = realTypeMM.getName().getQualifiedName();
				}
				Class< ? > clazz = Class.forName(className);

				Element elem = DesigntimeMethodStoragePlace.cs.getPropertyForObjectTypeByName(typeCode, fullName);
				boolean hideForProps = RepositoryHelper.hideForProperties(fullName, clazz, null);

				if (!RepositoryHelper.forceHideInDocs(fullName, clazz, realTypeMM.getDisplayType()))
				{
					if (elem != null && RepositoryHelper.hideForMobileProperties(fullName, clazz, realTypeMM.getDisplayType(), realTypeMM.isButton()))
					{
						if (newClientSupport != null)
						{
							newClientSupport = newClientSupport.remove(ClientSupport.mc);
							if (newClientSupport == null || newClientSupport == ClientSupport.None)//none
							{
								return new Pair<Boolean, ClientSupport>(Boolean.FALSE, ClientSupport.None);
							}

						}
					}

					if (elem == null &&
						(realTypeMM.getName().getQualifiedName().startsWith(
							com.servoy.j2db.documentation.persistence.docs.BaseDocsGraphicalComponentWithTitle.class.getPackage().getName()) &&
							((MethodMetaModel)memberMM).getClassName().startsWith(
								com.servoy.j2db.documentation.persistence.docs.BaseDocsGraphicalComponentWithTitle.class.getPackage().getName())))
					{
						// everything from docs package
						return new Pair<Boolean, ClientSupport>(Boolean.TRUE, newClientSupport);
					}

					if ((RepositoryHelper.shouldShow(fullName, elem, clazz, realTypeMM.getDisplayType()) && !hideForProps) ||
						RepositoryHelper.forceShowInDocs(fullName, clazz))
					{
						return new Pair<Boolean, ClientSupport>(Boolean.TRUE, newClientSupport);
					}
				}
			}
			catch (Throwable th)
			{
				LogUtil.logger().log(Level.SEVERE, "Exception while processing persist '" + realTypeMM.getName().getQualifiedName() + "'.", th);
			}
		}
		return new Pair<Boolean, ClientSupport>(Boolean.FALSE, null);
	}

	public void recompute(TypeMetaModel realTypeMM)
	{
		int typeCode = -1;
		AnnotationMetaModel sdoc = holder.getAnnotationManager().getAnnotation(realTypeMM, IPublicStore.ANNOTATION_SERVOY_DOCUMENTED);
		if (sdoc != null)
		{
			typeCode = ((Integer)sdoc.getAttribute("typeCode")).intValue();
		}
		if (typeCode == -1)
		{
			typeCode = IRepository.UNRESOLVED_ELEMENT;
		}

		Element elem = cs.getPropertyForObjectTypeByName(typeCode, fullSearchName);
		if (elem != null && elem.isDeprecated())
		{
			setDeprecated(true);
		}

		// SPECIAL CASES (not nice at all, but we need to adapt to how things are presented in the developer)
		if (getOfficialName().equals("extends") && realTypeMM.getName().getQualifiedName().equals(com.servoy.j2db.persistence.Form.class.getCanonicalName()))
		{
			newOfficialName = "extendsForm";
		}
		if (getOfficialName().equals("text") && realTypeMM.getName().getQualifiedName().equals(com.servoy.j2db.persistence.Field.class.getCanonicalName()))
		{
			newOfficialName = "titleText";
		}

	}

	public String getFullSearchName()
	{
		return fullSearchName;
	}

	private String cleanType(String type)
	{
		String convertedType = type.replaceAll("<[^>]*>", "");
		if (convertedType.indexOf('.') == -1)
		{
			for (String pkg : packagesToCheck)
			{
				String currName = pkg + convertedType;
				try
				{
					Class< ? > cc = Class.forName(currName);
					convertedType = cc.getCanonicalName();
					return convertedType;
				}
				catch (Throwable th)
				{
//					LogUtil.logger().log(Level.INFO, "Failed to load class '" + currName + "' for template parameter.");
				}
			}
		}
		return type;
	}

}
