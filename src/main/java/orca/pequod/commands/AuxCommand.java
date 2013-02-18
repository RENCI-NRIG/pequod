package orca.pequod.commands;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import orca.pequod.main.MainShell;

public class AuxCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="aux";
	private static final List<String> secondField = new LinkedList<String>();
	private static Map<String, SubCommand> subcommands = new HashMap<String, SubCommand>();
	
	static {
		subcommands.put("disconnect", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String url = l.next();
					return disconnectContainer(url);
				} catch(NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("connect", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String url = l.next();
					return connectContainer(url);
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("sleep", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String sec = l.next();
					int secInt;
					try {
						secInt = Integer.parseInt(sec);
						if (secInt <= 0)
							return null;
						Thread.sleep(secInt * 1000);
					} catch (NumberFormatException ee) {
						return null;
					} catch (InterruptedException ie) {
						;
					}
					return "Slept for " + sec + "seconds";
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		// second field is the commands 
		secondField.addAll(subcommands.keySet());
	}
	
	public AuxCommand() {
		mySubcommands = subcommands;
	}
	
	@Override
	public String getCommandName() {
		return COMMAND_NAME;
	}

	@Override
	public String getCommandShortDescription() {
		return "Auxiliary commands";
	}

	@Override
	public List<Completer> getCompleters() {
		List<Completer> ret = new LinkedList<Completer>();
		
		// get default completers (e.g. exit, history)
		List<String> defComp = defaultCompleters();
		// add the command
		defComp.add(COMMAND_NAME);
		
		ret.add(new ArgumentCompleter(new StringsCompleter(defComp),
				new StringsCompleter(secondField),
				new FileNameCompleter(),
				new NullCompleter()
				));
		return ret;
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
	}

	private static String disconnectContainer(String url) {
		MainShell.getInstance().getConnectionCache().shutdown(url);
		return "Disconnected " + url + "\n";
	}
	
	private static String connectContainer(String url) {
		MainShell.getInstance().getConnectionCache().shutdown(url);
		try {
			MainShell.getInstance().getConnectionCache().connect(url, null, null);
		} catch (URISyntaxException e) {
			return "Unable to connect to " + url + ": " + e;
		}
		return "Connected " + url + "\n";
	}
}
