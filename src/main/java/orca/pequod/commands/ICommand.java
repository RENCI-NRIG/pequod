package orca.pequod.commands;

import java.util.List;

import jline.console.completer.Completer;

/**
 * Prototypical command from which all other commands inherit.
 * Each command has its own
 * - options parser
 * - completer(s)
 * - name it responds to
 * - help
 * Each command can process a line of input
 * @author ibaldin
 *
 */
public interface ICommand {
	/**
	 * Return the completers for this command. Typically
	 * at least an ArgumentCompleter.
	 * @return - a list of completers
	 */
	public List<Completer> getCompleters();
	
	/**
	 * Get the command name this class implements
	 * @return
	 */
	public String getCommandName();
	
	/**
	 * Parse a line of input (either provided by JLine or
	 * direct from command line)
	 * @param l
	 */
	public String parseLine(String l);

	public String getCommandHelp();
	
	public String getCommandShortDescription();
	
	/**
	 * For commands maintaining state, called on shutdown
	 */
	public void shutdown();
}
