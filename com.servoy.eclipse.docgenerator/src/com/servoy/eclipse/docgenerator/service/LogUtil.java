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

package com.servoy.eclipse.docgenerator.service;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.servoy.eclipse.docgenerator.Activator;

/**
 * Utility class that allows us to write a log of the entire process
 * into a file. The standard Java Logging API is used.
 * 
 * @author gerzse
 */
public class LogUtil
{
	private static Logger theLogger = null;

	public static Logger logger()
	{
		if (theLogger == null)
		{
			theLogger = Logger.getLogger(Activator.PLUGIN_ID);
			// remove any existing handler; we'll add our own ones
			Handler existing[] = theLogger.getHandlers();
			for (Handler h : existing)
			{
				theLogger.removeHandler(h);
			}
			try
			{
				FileHandler fil = new FileHandler("servoy.log", 0, 1, false);
				fil.setFormatter(new SimpleFormatter());
				theLogger.addHandler(fil);
			}
			catch (IOException e)
			{
				System.err.println("Failed to register file handler for logging.");
				e.printStackTrace();
				ConsoleHandler con = new ConsoleHandler();
				con.setFormatter(new SimpleFormatter());
				theLogger.addHandler(con);
			}
			theLogger.setLevel(Level.ALL);
		}
		return theLogger;
	}
}
