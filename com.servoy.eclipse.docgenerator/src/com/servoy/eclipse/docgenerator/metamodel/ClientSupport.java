/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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

package com.servoy.eclipse.docgenerator.metamodel;

/**
 * Enum for tagging which clients are supported for the property.
 * @author rgansevles
 *
 */
public enum ClientSupport
{
	mc(1), wc(2), sc(4), mc_wc(mc.bits | wc.bits), mc_sc(mc.bits | sc.bits), wc_sc(wc.bits | sc.bits), mc_wc_sc(mc.bits | wc.bits | sc.bits);

	private final int bits;

	private ClientSupport(int bits)
	{
		this.bits = bits;
	}

	static ClientSupport fromString(String s)
	{
		if (s == null || s.length() == 0) return null;
		return fromBits(bits(s, mc) | bits(s, wc) | bits(s, sc));
	}

	private static int bits(String s, ClientSupport supp)
	{
		return s.indexOf(supp.name()) >= 0 ? supp.bits : 0;
	}

	public static ClientSupport fromAnnotation(AnnotationMetaModel csp)
	{
		if (csp == null) return null;
		return fromBits(bits(csp, mc) | bits(csp, wc) | bits(csp, sc));
	}

	private static int bits(AnnotationMetaModel csp, ClientSupport supp)
	{
		return Boolean.TRUE.equals(csp.getAttribute(supp.name())) ? supp.bits : 0;
	}

	private static ClientSupport fromBits(int bits)
	{
		for (ClientSupport supp : ClientSupport.values())
		{
			if (supp.bits == bits) return supp;
		}

		return null;
	}

	public String toAttribute()
	{
		return append(append(append(new StringBuilder(), mc), wc), sc).toString();
	}

	private StringBuilder append(StringBuilder sb, ClientSupport supp)
	{
		if ((bits & supp.bits) == supp.bits)
		{
			if (sb.length() > 0) sb.append(',');
			sb.append(supp.name());
		}
		return sb;
	}

}