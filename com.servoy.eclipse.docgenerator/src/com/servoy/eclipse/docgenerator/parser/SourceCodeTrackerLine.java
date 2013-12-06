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

/**
 * @author gerzse
 *
 * This class keeps information about one line of code in a compilation unit.
 * 
 * @see com.servoy.eclipse.docgenerator.parser.SourceCodeTracker
 */
public class SourceCodeTrackerLine
{
	private final String text;
	private final int startIndex;
	private final int endIndex;
	private final int lineIndex;

	public SourceCodeTrackerLine(String line, int positionIndex, int lineIndex)
	{
		this.text = line;
		this.startIndex = positionIndex;
		this.endIndex = positionIndex + text.length() - 1;
		this.lineIndex = lineIndex;
	}

	public int getPositionIndex()
	{
		return startIndex;
	}

	public int getLineIndex()
	{
		return lineIndex;
	}

	public boolean containsPosition(int index)
	{
		return (startIndex <= index) && (index <= endIndex);
	}

	public String getTextBetween(int reqStartIndex, int reqEndIndex)
	{
		int deltaStart = reqStartIndex - startIndex;
		int deltaEnd = endIndex - reqEndIndex;
		if (reqStartIndex <= endIndex && reqEndIndex >= startIndex)
		{
			String portion = text;
			if (deltaStart > 0)
			{
				portion = portion.substring(deltaStart);
			}
			if (deltaEnd > 0)
			{
				portion = portion.substring(0, portion.length() - deltaEnd);
			}
			return portion;
		}
		else
		{
			return null;
		}
	}

	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(startIndex).append(", ").append(endIndex).append(", ").append(lineIndex).append(": ->").append(text.replaceAll("[\\r\\n]*$", "")).append("<-");
		return sb.toString();
	}

}
