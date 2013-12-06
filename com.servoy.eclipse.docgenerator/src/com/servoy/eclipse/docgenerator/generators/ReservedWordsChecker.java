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

import java.util.HashSet;
import java.util.Set;

/**
 * @author gerzse
 */
public class ReservedWordsChecker
{
	// List of JS reserved keywords. When removing a "js_" or "jsFunction_" prefix, we don't want to hit one of these
	// words, or a Java reserved keyword. Java reserved keywords are checked via JDT, but JS keywords are checked manually.
	private static final Set<String> javascriptKeywords = new HashSet<String>();
	static
	{
		javascriptKeywords.add("break");
		javascriptKeywords.add("case");
		javascriptKeywords.add("catch");
		javascriptKeywords.add("continue");
		javascriptKeywords.add("debugger");
		javascriptKeywords.add("default");
		javascriptKeywords.add("delete");
		javascriptKeywords.add("do");
		javascriptKeywords.add("else");
		javascriptKeywords.add("finally");
		javascriptKeywords.add("for");
		javascriptKeywords.add("function");
		javascriptKeywords.add("if");
		javascriptKeywords.add("in");
		javascriptKeywords.add("instanceof");
		javascriptKeywords.add("new");
		javascriptKeywords.add("return");
		javascriptKeywords.add("switch");
		javascriptKeywords.add("this");
		javascriptKeywords.add("throw");
		javascriptKeywords.add("try");
		javascriptKeywords.add("typeof");
		javascriptKeywords.add("var");
		javascriptKeywords.add("void");
		javascriptKeywords.add("while");
		javascriptKeywords.add("with");
		javascriptKeywords.add("null");
	}

	public static boolean isReserved(String word)
	{
		if (javascriptKeywords.contains(word))
		{
			return true;
		}
		else
		{
			// TODO: also check for Java reserved words
			return false;
		}
	}

}
