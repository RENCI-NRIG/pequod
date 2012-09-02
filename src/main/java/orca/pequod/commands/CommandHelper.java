package orca.pequod.commands;

import java.io.InputStream;

/** 
 * Helper methods for implementing commands
 * @author ibaldin
 *
 */
public class CommandHelper {
	/**
	 * Uses class name to determine what file to read
	 * E.g. HelpCommand class should have HelpCommand.txt
	 * file with help text
	 * @return
	 */
	String readHelpFile() {
		String name = "/" + this.getClass().getCanonicalName().replace(".", "/") + ".txt";
		
		InputStream is = this.getClass().getResourceAsStream(name);
		if (is != null)
			return new java.util.Scanner(is).useDelimiter("\\A").next();
		else
			return "Unable to load help file " + name;
	}
}
