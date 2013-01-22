package orca.pequod.commands;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import orca.pequod.main.MainShell;

/** 
 * Helper methods for implementing commands
 * @author ibaldin
 *
 */
public abstract class CommandHelper {
	protected Map<String, SubCommand> mySubcommands;
	
	CommandHelper() {
		mySubcommands = null;
	}
	
	protected abstract String getCommandName();
	
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
	
	protected String syntaxError() {
		return "ERROR: Syntax error.\n" + getCommandHelp();
	}

	public String getCommandHelp() {
		return readHelpFile();
	}
	
	public String parseLine(String l) {
		Scanner scanner = new Scanner(l);
		try {
			if (getCommandName().equals(scanner.next())) {
				// get the subcommand
				String scLast = scanner.next();
				SubCommand s = mySubcommands.get(scLast);
				if (s == null)
					return syntaxError();
				String ret = s.parse(scanner, scLast);
				if (ret == null)
					return syntaxError();
				return ret;
			} else
				return syntaxError();
		} catch (NoSuchElementException e) {
			return syntaxError();
		}
	}
	
	protected List<String> defaultCompleters() {
		List<String> ret = new LinkedList<String>();
		
		ret.add(MainShell.EXIT_COMMAND);
		ret.add(MainShell.HISTORY_COMMAND);
		
		return ret;
	}
}
