package orca.pequod.commands;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import orca.pequod.main.MainShell;

/**
 * Special command that can peek at other available
 * commands and display their help
 * @author ibaldin
 *
 */
public class HelpCommand extends CommandHelper implements ICommand{
	public static String COMMAND_NAME="help";
	
	public HelpCommand() {

	}

	public String getCommandName() {
		return COMMAND_NAME;
	}

	public String getCommandShortDescription() {
		return "Returns help for individual commands";
	}

	public List<Completer> getCompleters() {
		List<Completer> ret = new LinkedList<Completer>();
		
		// get default completers (e.g. exit, history)
		List<String> defComp = defaultCompleters();
		// add the command
		defComp.add(COMMAND_NAME);
		
		ret.add(new ArgumentCompleter(new StringsCompleter(defComp),
				new StringsCompleter(MainShell.getInstance().getCommands().keySet()),
				new NullCompleter()));
		
		return ret;
	}

	public String parseLine(String l) {
		Scanner scanner = new Scanner(l);
		
		try {
			if (COMMAND_NAME.equals(scanner.next())) {
				ICommand cmd = MainShell.getInstance().getCommands().get(scanner.next());
				if (cmd != null)
					return cmd.getCommandHelp();
				else
					return getCommandHelp();
			}
			return syntaxError();
		} catch (NoSuchElementException e) {
			return getCommandHelp();
		}
	}

	@Override
	public String getCommandHelp() {
		// if in subcommand, return sub-command help,
		// otherwise top-level help
		if (MainShell.getInstance().getSubCommand() != null) {
			if (MainShell.getInstance().getSubCommand().getCommandName().equals(COMMAND_NAME))
				return super.getCommandHelp();
			else
				return MainShell.getInstance().getSubCommand().getCommandHelp();
		} else
			return MainShell.getInstance().getAllCommandsHelp();
	}
	
	public void shutdown() {
		// nothing to do
	}
}
