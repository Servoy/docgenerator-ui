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

package com.servoy.eclipse.docgenerator.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.servoy.eclipse.docgenerator.metamodel.AnnotationMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.AnnotationsList;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning;
import com.servoy.eclipse.docgenerator.metamodel.DocumentationWarning.WarningType;
import com.servoy.eclipse.docgenerator.metamodel.FieldMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.IJavadocPartsHolder;
import com.servoy.eclipse.docgenerator.metamodel.JavadocMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.JavadocTagPart;
import com.servoy.eclipse.docgenerator.metamodel.JavadocTextPart;
import com.servoy.eclipse.docgenerator.metamodel.MemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.MethodMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.ReferenceMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.ReferenceMetaModel.QualifiedNameDisplayState;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.service.LogUtil;


/**
 * AST visitor that extracts relevant information about classes and their members
 * and also parses Javadocs and extracts the information from them.
 *
 * @author gerzse
 */
public class JavadocExtractor extends ASTVisitor
{
	// registry with all parsed types
	private final MetaModelHolder allTypes = new MetaModelHolder();

	// types (classes/interfaces), methods and fields
	private String packageName;
	private final Stack<TypeMetaModel> typesStack = new Stack<TypeMetaModel>();
	private final List<MemberMetaModel> currentMembers = new ArrayList<MemberMetaModel>();

	// javadocs and annotations
	private JavadocMetaModel currentJavadoc;
	private final Stack<IJavadocPartsHolder> javadocsStack = new Stack<IJavadocPartsHolder>();
	private final Stack<AnnotationsList> annotationsStack = new Stack<AnnotationsList>();

	// for manual tracking of whitespace (JDT does not store whitespace in the AST)
	private int lastNodeEnd = -1;
	private SourceCodeTracker tracker;

	public MetaModelHolder getRawDataHolder()
	{
		return allTypes;
	}

	public void setSourceCodeTracker(SourceCodeTracker tracker)
	{
		this.tracker = tracker;
	}

	@Override
	public boolean visit(PackageDeclaration node)
	{
		packageName = node.getName().getFullyQualifiedName();
		return false;
	}

	@Override
	public boolean visit(ImportDeclaration node)
	{
		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node)
	{
		startNewType(node);
		return true;
	}

	@Override
	public void endVisit(TypeDeclaration node)
	{
		addCurrentType();
	}

	@Override
	public boolean visit(EnumDeclaration node)
	{
		startNewType(node);
		return true;
	}

	@Override
	public void endVisit(EnumDeclaration node)
	{
		addCurrentType();
	}

	@Override
	public boolean visit(EnumConstantDeclaration node)
	{
		if (typesStack.isEmpty())
		{
			return false;
		}

		FieldMetaModel fieldMM = new FieldMetaModel(typesStack.peek().getName().getQualifiedName(), node, node.getName().getFullyQualifiedName());
		typesStack.peek().addMember(fieldMM.getIndexSignature(), fieldMM);
		currentMembers.add(fieldMM);

		annotationsStack.push(new AnnotationsList());
		return true;
	}

	@Override
	public void endVisit(EnumConstantDeclaration node)
	{
		if (!typesStack.isEmpty())
		{
			AnnotationsList ann = annotationsStack.pop();
			for (MemberMetaModel memberMM : currentMembers)
			{
				memberMM.setAnnotations(ann);
			}
			currentMembers.clear();
		}
	}

	private void startNewType(AbstractTypeDeclaration node)
	{
		List<String> ancestorNames = new ArrayList<String>();
		for (TypeMetaModel ancestorType : typesStack)
		{
			ancestorNames.add(ancestorType.getName().getShortName());
		}
		TypeMetaModel typeData = node instanceof TypeDeclaration typeDeclaration
			? new TypeMetaModel(packageName, ancestorNames, typeDeclaration, typeDeclaration.isInterface())
			: new TypeMetaModel(packageName, ancestorNames, (EnumDeclaration)node);
		typesStack.push(typeData);
		annotationsStack.push(new AnnotationsList());
	}

	private void addCurrentType()
	{
		TypeMetaModel typeMM = typesStack.pop();
		AnnotationsList annotations = annotationsStack.pop();
		typeMM.setAnnotations(annotations);
		allTypes.addType(typeMM.getName().getQualifiedName(), typeMM);

	}

	@Override
	public boolean visit(MethodDeclaration node)
	{
		if (typesStack.isEmpty())
		{
			return false;
		}

		MethodMetaModel methodMM = new MethodMetaModel(typesStack.peek().getName().getQualifiedName(), node);
		typesStack.peek().addMember(methodMM.getIndexSignature(), methodMM);
		currentMembers.add(methodMM);
		annotationsStack.push(new AnnotationsList());
		return true;
	}

	@Override
	public void endVisit(MethodDeclaration node)
	{
		if (!typesStack.isEmpty())
		{
			AnnotationsList annotations = annotationsStack.pop();
			for (MemberMetaModel memberMM : currentMembers)
			{
				memberMM.setAnnotations(annotations);
			}
			currentMembers.clear();
		}
	}

	@Override
	public boolean visit(FieldDeclaration node)
	{
		if (typesStack.isEmpty())
		{
			return false;
		}

		for (Object o : node.fragments())
		{
			if (o instanceof VariableDeclarationFragment)
			{
				VariableDeclarationFragment varDecl = (VariableDeclarationFragment)o;
				FieldMetaModel fieldMM = new FieldMetaModel(typesStack.peek().getName().getQualifiedName(), node, varDecl.getName().getFullyQualifiedName());
				typesStack.peek().addMember(fieldMM.getIndexSignature(), fieldMM);
				currentMembers.add(fieldMM);
			}
		}
		annotationsStack.push(new AnnotationsList());
		return true;
	}

	@Override
	public void endVisit(FieldDeclaration node)
	{
		if (!typesStack.isEmpty())
		{
			AnnotationsList ann = annotationsStack.pop();
			for (MemberMetaModel memberMM : currentMembers)
			{
				memberMM.setAnnotations(ann);
			}
			currentMembers.clear();
		}
	}

	@Override
	public boolean visit(MarkerAnnotation node)
	{
		handleAnnotation(node);
		return false;
	}

	@Override
	public boolean visit(NormalAnnotation node)
	{
		handleAnnotation(node);
		return false;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node)
	{
		handleAnnotation(node);
		return false;
	}

	private void handleAnnotation(Annotation node)
	{
		if (!annotationsStack.isEmpty())
		{
			AnnotationMetaModel annotationMM;
			IAnnotationBinding bind = node.resolveAnnotationBinding();
			if (bind != null)
			{
				annotationMM = (AnnotationMetaModel)extractAnnotationValue(bind);
			}
			else
			{
				annotationMM = new AnnotationMetaModel(node.getTypeName().getFullyQualifiedName());
				if (node instanceof NormalAnnotation)
				{
					NormalAnnotation na = (NormalAnnotation)node;
					for (Object pair : na.values())
					{
						if (pair instanceof MemberValuePair)
						{
							MemberValuePair mvPair = (MemberValuePair)pair;
							String key = mvPair.getName().getFullyQualifiedName();
							Object valObj = mvPair.getValue().resolveConstantExpressionValue();
							if (valObj != null)
							{
								annotationMM.addAttribute(key, valObj);
							}
							else
							{
								warning(WarningType.Other, "Cannot retrieve value for attribute '" + key + "' of annotation: " + node.toString());
								annotationMM.addAttribute(key, mvPair.getValue());
							}
						}
					}
				}
				else if (node instanceof SingleMemberAnnotation)
				{
					SingleMemberAnnotation sma = (SingleMemberAnnotation)node;
					Object value = sma.getValue().resolveConstantExpressionValue();
					if (value != null)
					{
						annotationMM.addAttribute("value", value);
					}
					else
					{
						warning(WarningType.Other, "Cannot retrieve value from single member annotation: " + node.toString());
						annotationMM.addAttribute("value", sma.getValue());
					}
				}
			}
			annotationsStack.peek().add(annotationMM.getName(), annotationMM);
		}
	}

	private Object extractAnnotationValue(Object value)
	{
		if (value instanceof IVariableBinding)
		{
			IVariableBinding varBinding = (IVariableBinding)value;
			String typeQualifiedName = null;
			String typeSimpleName = null;
			if (varBinding.getDeclaringClass() != null)
			{
				typeQualifiedName = varBinding.getDeclaringClass().getQualifiedName();
				typeSimpleName = varBinding.getDeclaringClass().getName();
			}
			return new ReferenceMetaModel(typeQualifiedName, typeSimpleName, varBinding.getName(), null, QualifiedNameDisplayState.Simple);
		}
		else if (value instanceof IAnnotationBinding)
		{
			IAnnotationBinding annBinding = (IAnnotationBinding)value;
			AnnotationMetaModel annot = new AnnotationMetaModel(annBinding.getName());
			for (IMemberValuePairBinding attr : annBinding.getAllMemberValuePairs())
			{
				try
				{
					String key = attr.getName();
					Object val = extractAnnotationValue(attr.getValue());
					annot.addAttribute(key, val);
				}
				catch (Exception e)
				{
					System.err.println("Attribute " + attr.getName() + " value is null (annotation binding " + annBinding.getName() + ")");
				}
			}
			return annot;
		}
		else if (value.getClass().isArray())
		{
			Object[] values = (Object[])value;
			Object[] extractedValues = new Object[values.length];
			for (int i = 0; i < values.length; i++)
			{
				extractedValues[i] = extractAnnotationValue(values[i]);
			}
			return extractedValues;
		}
		else
		{
			return value;
		}
	}

	@Override
	public boolean visit(Javadoc node)
	{
		if (isInterestingJavadoc(node))
		{
			currentJavadoc = new JavadocMetaModel();
			javadocsStack.push(currentJavadoc);
			return true;
		}
		return false;
	}

	@Override
	public void endVisit(Javadoc node)
	{
		if (isInterestingJavadoc(node))
		{
			javadocsStack.pop();
			currentJavadoc.compress();
			int parentType = node.getParent().getNodeType();
			if (parentType == ASTNode.TYPE_DECLARATION || parentType == ASTNode.ENUM_DECLARATION)
			{
				typesStack.peek().setJavadoc(currentJavadoc);
			}
			else if (parentType == ASTNode.METHOD_DECLARATION || parentType == ASTNode.FIELD_DECLARATION || parentType == ASTNode.ENUM_CONSTANT_DECLARATION)
			{
				for (MemberMetaModel memberMM : currentMembers)
				{
					memberMM.setJavadoc(currentJavadoc);
				}
			}
			currentJavadoc = null;
			lastNodeEnd = -1;
		}
	}

	private boolean isInterestingJavadoc(Javadoc node)
	{
		if (node.getParent() != null)
		{
			int parentType = node.getParent().getNodeType();
			if (parentType == ASTNode.TYPE_DECLARATION || parentType == ASTNode.METHOD_DECLARATION || parentType == ASTNode.FIELD_DECLARATION ||
				parentType == ASTNode.ENUM_DECLARATION || parentType == ASTNode.ENUM_CONSTANT_DECLARATION)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean visit(TagElement node)
	{
		String tagName;
		if (node.getTagName() == null)
		{
			tagName = JavadocMetaModel.TEXT_TAG;
		}
		else
		{
			tagName = node.getTagName();
		}
		JavadocTagPart javadocTag = new JavadocTagPart(tagName);

		int prefixLen;
		if (node.getTagName() == null)
		{
			prefixLen = 0;
		}
		else
		{
			prefixLen = node.getTagName().length();
			if (node.isNested())
			{
				prefixLen += 1;
			}
		}
		storeWhitespaceIfAny(node, prefixLen);

		javadocsStack.peek().addPart(javadocTag);
		javadocsStack.push(javadocTag);

		return true;
	}

	@Override
	public void endVisit(TagElement node)
	{
		javadocsStack.pop();
		if (node.isNested())
		{
			lastNodeEnd = node.getStartPosition() + node.getLength() - 1;
		}
	}

	@Override
	public boolean visit(MemberRef node)
	{
		storeWhitespaceIfAny(node, -1);

		ReferenceMetaModel ref = extractBinding(node.resolveBinding(), node.getQualifier(), node.getName(), null);
		javadocsStack.peek().addPart(ref);

		return false;
	}

	@Override
	public boolean visit(MethodRef node)
	{
		storeWhitespaceIfAny(node, -1);

		ReferenceMetaModel ref = extractBinding(node.resolveBinding(), node.getQualifier(), node.getName(), node.parameters());
		javadocsStack.peek().addPart(ref);

		return false;
	}

	private ReferenceMetaModel extractBinding(IBinding bnd, Name qualifier, SimpleName name, List< ? > parameters)
	{
		if (bnd != null)
		{
			String typeQualifiedName = null;
			String typeSimpleName = null;
			String[] argumentsTypesNames = null;
			QualifiedNameDisplayState qnameState = QualifiedNameDisplayState.None;
			if (bnd instanceof IVariableBinding)
			{
//				IVariableBinding varBind = (IVariableBinding)bnd;
//				if (varBind.getDeclaringClass() != null)
//				{
//					typeQualifiedName = varBind.getDeclaringClass().getQualifiedName();
//					typeSimpleName = varBind.getDeclaringClass().getName();
//				}
			}
			else if (bnd instanceof IMethodBinding)
			{
				IMethodBinding methBind = (IMethodBinding)bnd;
//				if (methBind.getDeclaringClass() != null)
//				{
//					typeQualifiedName = methBind.getDeclaringClass().getQualifiedName();
//					typeSimpleName = methBind.getDeclaringClass().getName();
//				}
				ITypeBinding[] parTypes = methBind.getParameterTypes();
				argumentsTypesNames = new String[parTypes.length];
				for (int i = 0; i < parTypes.length; i++)
				{
					argumentsTypesNames[i] = parTypes[i].getName();
				}
			}
			if (qualifier != null)
			{
				typeQualifiedName = qualifier.getFullyQualifiedName();
				int idx = typeQualifiedName.lastIndexOf('.');
				if (idx >= 0)
				{
					qnameState = QualifiedNameDisplayState.Full;
					typeSimpleName = typeQualifiedName.substring(idx + 1);
				}
				else
				{
					qnameState = QualifiedNameDisplayState.Simple;
					typeSimpleName = typeQualifiedName;
				}
			}
			ReferenceMetaModel ref = new ReferenceMetaModel(typeQualifiedName, typeSimpleName, bnd.getName(), argumentsTypesNames, qnameState);
			return ref;
		}
		else
		{
			String typeQualifiedName = null;
			String typeSimpleName = null;
			QualifiedNameDisplayState qnameState = QualifiedNameDisplayState.None;
			if (qualifier != null)
			{
				typeQualifiedName = qualifier.getFullyQualifiedName();
				int idx = typeQualifiedName.lastIndexOf('.');
				if (idx > 0)
				{
					typeSimpleName = typeQualifiedName.substring(idx + 1);
					qnameState = QualifiedNameDisplayState.Full;
				}
				else
				{
					typeSimpleName = typeQualifiedName;
					qnameState = QualifiedNameDisplayState.Simple;
				}
			}
			String[] argumentsTypesNames = null;
			if (parameters != null)
			{
				argumentsTypesNames = new String[parameters.size()];
				for (int i = 0; i < parameters.size(); i++)
				{
					Object oo = parameters.get(i);
					if (oo instanceof MethodRefParameter)
					{
						MethodRefParameter par = (MethodRefParameter)oo;
						if (par.getType() != null)
						{
							ITypeBinding tBind = par.getType().resolveBinding();
							if (tBind != null)
							{
								argumentsTypesNames[i] = tBind.getQualifiedName();
							}
							else
							{
								argumentsTypesNames[i] = par.getType().toString();
							}
						}
						else
						{
							warning(WarningType.Other, "Missing type for parameter: " + oo.toString());
							argumentsTypesNames[i] = "?";
						}
					}
					else
					{
						warning(WarningType.Other, "Invalid type of parameter: " + oo.toString());
						argumentsTypesNames[i] = oo.toString();
					}
				}
			}
			ReferenceMetaModel ref = new ReferenceMetaModel(typeQualifiedName, typeSimpleName, name.getFullyQualifiedName(), argumentsTypesNames, qnameState);
			return ref;
		}
	}

	@Override
	public boolean visit(SimpleName node)
	{
		return visitNameEx(node);
	}

	@Override
	public boolean visit(QualifiedName node)
	{
		return visitNameEx(node);
	}

	private boolean visitNameEx(Name node)
	{
		if (!javadocsStack.isEmpty())
		{
			storeWhitespaceIfAny(node, -1);

			JavadocTextPart newEntry = new JavadocTextPart(node.toString());
			javadocsStack.peek().addPart(newEntry);
		}

		return false;
	}

	@Override
	public boolean visit(TextElement node)
	{
		storeWhitespaceIfAny(node, -1);

		JavadocTextPart newEntry = new JavadocTextPart(node.toString());
		javadocsStack.peek().addPart(newEntry);

		return false;
	}

	private void storeWhitespaceIfAny(ASTNode node, int prefixLen)
	{
		if (lastNodeEnd != -1)
		{
			int thisNodeStart = node.getStartPosition();
			if (tracker != null)
			{
				String prefix = tracker.getTextBetween(lastNodeEnd + 1, thisNodeStart - 1);
				if (prefix != null)
				{
					if (!javadocsStack.isEmpty())
					{
						// remove "   * " from beginning of comment lines, including the first space if it's there
						prefix = Pattern.compile("^[ \\t]*\\*[ ]?", Pattern.MULTILINE).matcher(prefix).replaceAll("");
						// transform line endings to Linux style
						prefix = Pattern.compile("\\r\\n").matcher(prefix).replaceAll("\n");
						prefix = Pattern.compile("\\r").matcher(prefix).replaceAll("\n");
						JavadocTextPart prefixPart = new JavadocTextPart(prefix);
						javadocsStack.peek().addPart(prefixPart);
					}
					else
					{
						LogUtil.logger().severe("Strange, but the stack is empty.");
					}
				}
				else
				{
					LogUtil.logger().severe("Strange error when reading space between " + lastNodeEnd + " and " + thisNodeStart);
				}
			}
		}
		int lenToUse = node.getLength();
		if (prefixLen >= 0) lenToUse = prefixLen;
		lastNodeEnd = node.getStartPosition() + lenToUse - 1;
	}

	private void warning(WarningType type, String s)
	{
		if (typesStack.size() > 0)
		{
			DocumentationWarning dw = new DocumentationWarning(type, location(), s);
			if (currentMembers.size() > 0)
			{
				for (MemberMetaModel mdr : currentMembers)
				{
					mdr.getWarnings().add(dw);
				}
			}
			else
			{
				typesStack.peek().getWarnings().add(dw);
			}
		}
	}

	private String location()
	{
		StringBuffer sb = new StringBuffer();
		if (typesStack.size() > 0)
		{
			if (currentMembers.size() > 0)
			{
				sb.append(currentMembers.get(0).getFullSignature());
			}
			else
			{
				sb.append(typesStack.peek().getName().getQualifiedName());
			}
		}
		return sb.toString();
	}
}
