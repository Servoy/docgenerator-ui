/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2012 Servoy BV

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

package com.servoy.eclipse.docgenerator.annotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.servoy.eclipse.docgenerator.annotations.AnnotationManager.IAnnotatedClass;
import com.servoy.eclipse.docgenerator.annotations.AnnotationManager.IAnnotatedField;
import com.servoy.eclipse.docgenerator.annotations.AnnotationManager.IAnnotatedMethod;
import com.servoy.eclipse.docgenerator.metamodel.AnnotationMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.FieldMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.IMemberMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.MetaModelHolder;
import com.servoy.eclipse.docgenerator.metamodel.MethodMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeMetaModel;
import com.servoy.eclipse.docgenerator.metamodel.TypeName;


/**
 * Handles annotations form classes, caches annotations defined for the class itself and for the interfaces it implements.
 * 
 * Annotations have to defined at element (method or field) level, or at interface or class level.
 * When an annotation is defined at class or interface, all elements in that class are annotated, but not all methods from classes inheriting from that interface or class.
 * 
 * @author rgansevles
 *
 */
public class AnnotationManagerJdt
{
	private final MetaModelHolder holder;
	private final AnnotationManager<AnnotationMetaModel, String> annotationManager = new AnnotationManager<AnnotationMetaModel, String>();

	public AnnotationManagerJdt(MetaModelHolder holder)
	{
		this.holder = holder;
	}

	public boolean hasAnnotation(MethodMetaModel method, TypeMetaModel originalClass, String annotationClass)
	{
		return getAnnotation(method, originalClass, annotationClass) != null;
	}

	public AnnotationMetaModel getAnnotation(MethodMetaModel method, TypeMetaModel originalClass, String annotationClass)
	{
		if (method == null) return null;
		return annotationManager.getAnnotation(new JdtAnnotatedMethod(holder, method), new JdtAnnotatedClass(holder, originalClass), annotationClass);
	}

	public boolean hasAnnotation(FieldMetaModel field, String annotationClass)
	{
		return getAnnotation(field, annotationClass) != null;
	}

	public AnnotationMetaModel getAnnotation(FieldMetaModel field, String annotationClass)
	{
		if (field == null) return null;
		return annotationManager.getAnnotation(new JdtAnnotatedField(holder, field), annotationClass);
	}

	public boolean hasAnnotation(TypeMetaModel targetClass, String annotationClass)
	{
		return getAnnotation(targetClass, annotationClass) != null;
	}

	public AnnotationMetaModel getAnnotation(TypeMetaModel targetClass, String annotationClass)
	{
		if (targetClass == null) return null;
		return annotationManager.getAnnotation(new JdtAnnotatedClass(holder, targetClass), annotationClass);
	}

	//////////// Wrapper classes //////////////////

	private static class JdtAnnotatedClass implements IAnnotatedClass<AnnotationMetaModel, String>
	{
		private final MetaModelHolder holder;
		private final TypeMetaModel originalClass;

		private JdtAnnotatedClass(MetaModelHolder holder, TypeMetaModel originalClass)
		{
			this.holder = holder;
			if (originalClass == null) throw new NullPointerException("class"); //$NON-NLS-1$
			this.originalClass = originalClass;
		}

		public TypeMetaModel getOriginalClass()
		{
			return originalClass;
		}

		@Override
		public AnnotationMetaModel getAnnotation(String searchedAnnotation)
		{
			return originalClass.getAnnotations().getAnnotation(searchedAnnotation);
		}

		@Override
		public IAnnotatedClass<AnnotationMetaModel, String> getSuperclass()
		{
			TypeMetaModel sup = holder.getType(originalClass.getSupertype());
			return sup == null ? null : new JdtAnnotatedClass(holder, sup);
		}

		@Override
		public IAnnotatedField<AnnotationMetaModel, String> getField(String name)
		{
			IMemberMetaModel member = originalClass.getMember(name, holder);
			if (member instanceof FieldMetaModel)
			{
				return new JdtAnnotatedField(holder, (FieldMetaModel)member);
			}

			return null;
		}

		@Override
		public Collection<IAnnotatedClass<AnnotationMetaModel, String>> getInterfaces()
		{
			List<TypeName> intfs = originalClass.getInterfaces();
			List<IAnnotatedClass<AnnotationMetaModel, String>> interfaces = new ArrayList<IAnnotatedClass<AnnotationMetaModel, String>>(intfs.size());
			for (TypeName intf : intfs)
			{
				TypeMetaModel tmm = holder.getType(intf);
				if (tmm != null)
				{
					interfaces.add(new JdtAnnotatedClass(holder, tmm));
				}
			}
			return interfaces;
		}

		@Override
		public boolean isAssignableFrom(IAnnotatedClass<AnnotationMetaModel, String> declaringClass)
		{
			return declaringClass != null && isAssignableFrom(((JdtAnnotatedClass)declaringClass).getOriginalClass());
		}

		private boolean isAssignableFrom(TypeMetaModel otherClass)
		{
			if (otherClass == null)
			{
				return false;
			}

			if (originalClass.equals(otherClass))
			{
				return true;
			}

			if (originalClass.isInterface())
			{
				for (TypeName intf : otherClass.getInterfaces())
				{
					if (isAssignableFrom(holder.getType(intf)))
					{
						return true;
					}
				}
			}

			return isAssignableFrom(holder.getType(otherClass.getSupertype()));
		}

		@Override
		public IAnnotatedMethod<AnnotationMetaModel, String> getMethod(Object signature)
		{
			IMemberMetaModel member = originalClass.getMember((String)signature, holder);
			if (member instanceof MethodMetaModel)
			{
				return new JdtAnnotatedMethod(holder, (MethodMetaModel)member);
			}

			return null;
		}

		@Override
		public int hashCode()
		{
			return originalClass.hashCode();
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			JdtAnnotatedClass other = (JdtAnnotatedClass)obj;
			if (originalClass == null)
			{
				if (other.originalClass != null) return false;
			}
			else if (!originalClass.equals(other.originalClass)) return false;
			return true;
		}
	}

	private static class JdtAnnotatedField implements IAnnotatedField<AnnotationMetaModel, String>
	{
		private final MetaModelHolder holder;
		private final FieldMetaModel field;

		private JdtAnnotatedField(MetaModelHolder holder, FieldMetaModel field)
		{
			this.holder = holder;
			this.field = field;
		}

		@Override
		public AnnotationMetaModel getAnnotation(String searchedAnnotation)
		{
			return field.getAnnotations().getAnnotation(searchedAnnotation);
		}

		@Override
		public String getName()
		{
			return field.getName();
		}

		@Override
		public IAnnotatedClass<AnnotationMetaModel, String> getDeclaringClass()
		{
			TypeMetaModel tmm = holder.getType(field.getClassName());
			return tmm == null ? null : new JdtAnnotatedClass(holder, tmm);
		}

		@Override
		public int hashCode()
		{
			return field.hashCode();
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			JdtAnnotatedField other = (JdtAnnotatedField)obj;
			if (field == null)
			{
				if (other.field != null) return false;
			}
			else if (!field.equals(other.field)) return false;
			return true;
		}
	}

	private static class JdtAnnotatedMethod implements IAnnotatedMethod<AnnotationMetaModel, String>
	{
		private final MetaModelHolder holder;
		private final MethodMetaModel method;

		private JdtAnnotatedMethod(MetaModelHolder holder, MethodMetaModel method)
		{
			this.holder = holder;
			this.method = method;
		}

		@Override
		public AnnotationMetaModel getAnnotation(String searchedAnnotation)
		{
			return method.getAnnotations().getAnnotation(searchedAnnotation);
		}

		@Override
		public Object getSignature()
		{
			return method.getIndexSignature();
		}

		@Override
		public IAnnotatedClass<AnnotationMetaModel, String> getDeclaringClass()
		{
			TypeMetaModel tmm = holder.getType(method.getClassName());
			return tmm == null ? null : new JdtAnnotatedClass(holder, tmm);
		}

		@Override
		public int hashCode()
		{
			return method.hashCode();
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			JdtAnnotatedMethod other = (JdtAnnotatedMethod)obj;
			if (method == null)
			{
				if (other.method != null) return false;
			}
			else if (!method.equals(other.method)) return false;
			return true;
		}
	}
}
