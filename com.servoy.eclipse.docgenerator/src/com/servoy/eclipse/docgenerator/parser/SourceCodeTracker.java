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

/**
 * @author gerzse
 *
 * The AST does not track whitespace for Javadocs, but it does track the starting
 * position of each token. We rebuild the whitespace manually by analyzing the 
 * source code in the compilation unit. 
 */
@SuppressWarnings("nls")
public class SourceCodeTracker
{
	private final ArrayList<SourceCodeTrackerLine> lines = new ArrayList<SourceCodeTrackerLine>();

	public SourceCodeTracker(String allCode)
	{
		StringBuffer buffer = new StringBuffer();
		int currentLineIndex = 0;
		int currentLineStartPosition = 0;
		for (int i = 0; i < allCode.length(); i++)
		{
			char c = allCode.charAt(i);
			buffer.append(c);
			if (c == '\r' || c == '\n')
			{
				// check for '\r\n' combination
				if ((c == '\r') && (i + 1 < allCode.length()))
				{
					char next = allCode.charAt(i + 1);
					if (next == '\n')
					{
						buffer.append(next);
						// skip a char
						i++;
					}
				}

				// flush the line
				if (buffer.length() > 0)
				{
					SourceCodeTrackerLine lineTracker = new SourceCodeTrackerLine(buffer.toString(), currentLineStartPosition, currentLineIndex);
					lines.add(lineTracker);
				}
				buffer.delete(0, buffer.length());
				currentLineStartPosition = i + 1;
				currentLineIndex++;
			}
		}
	}

	public String getTextBetween(int startIndex, int endIndex)
	{
		int startLine = findLineIndex(startIndex);
		int endLine = findLineIndex(endIndex);
		if (startLine != -1 && endLine != -1)
		{
			StringBuffer sb = new StringBuffer();
			for (int i = startLine; i <= endLine; i++)
			{
				SourceCodeTrackerLine line = lines.get(i);
				String lineText = line.getTextBetween(startIndex, endIndex);
				sb.append(lineText);
			}
			return sb.toString();
		}
		else
		{
			return null;
		}
	}

	private int findLineIndex(int positionIndex)
	{
		// binary search to find the line which contains the given position
		int left = 0;
		int right = lines.size() - 1;
		while (left <= right)
		{
			int mid = (left + right) / 2;
			SourceCodeTrackerLine line = lines.get(mid);
			if (line.containsPosition(positionIndex))
			{
				return mid;
			}
			else
			{
				if (line.getPositionIndex() > positionIndex)
				{
					right = mid - 1;
				}
				else
				{
					left = mid + 1;
				}
			}
		}
		return -1;
	}

	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		for (SourceCodeTrackerLine line : lines)
		{
			sb.append(line.toString());
			sb.append("\n");
		}
		return sb.toString();
	}
}
