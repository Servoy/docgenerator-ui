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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.TreeMap;

import com.servoy.j2db.persistence.ArgumentType;
import com.servoy.j2db.persistence.ClientMethodTemplatesLoader;
import com.servoy.j2db.persistence.MethodArgument;
import com.servoy.j2db.persistence.MethodTemplate;

/**
 * @author gerzse
 */
public class MethodTemplatesPropertiesGenerator extends MethodTemplatesXmlGenerator
{
	@Override
	protected InputStream generateContent(TreeMap<String, MethodTemplate> templates) throws Exception
	{
		TreeMap<String, Integer> properties = new TreeMap<String, Integer>();

		for (String name : templates.keySet())
		{
			MethodTemplate mt = templates.get(name);

			MethodArgument[] methodArguments = mt.getArguments();
			for (int i = 0; methodArguments != null && i < methodArguments.length; i++)
			{
				if (methodArguments[i].getType() == ArgumentType.JSEvent)
				{
					// method template declares an event argument
					properties.put(ClientMethodTemplatesLoader.JSEVENT_ARG_LOCATION_PREFIX + name, Integer.valueOf(i));
					break;
				}
			}
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintWriter pw = new PrintWriter(baos);
		for (String key : properties.keySet())
		{
			Integer val = properties.get(key);
			pw.println(key + "=" + val.toString());
		}
		pw.close();
		baos.close();

		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		return bais;
	}

}
