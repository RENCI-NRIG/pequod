package orca.pequod.commands;

import java.security.cert.Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import orca.manage.IOrcaContainer;
import orca.manage.beans.ActorMng;
import orca.manage.beans.UserMng;
import orca.pequod.main.MainShell;

public class ShowCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="show";
	private static String[] thirdField = {"for"};
	private static String[] fifthField = {"all"};
	
	private static final List<String> secondField = new LinkedList<String>();
	private static final Map<String, SubCommand> subcommands = new HashMap<String, SubCommand>();
	
	/**
	 * Implementations of various subcommands
	 */
	static {
		subcommands.put("containers", new SubCommand() {
			public String parse(Scanner l) {
				String ret = "";
				// return a list of containers and their status
				for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
					ret += "  " + c + ": " + (MainShell.getInstance().getConnectionCache().isConnectionInError(c) ?
							MainShell.getInstance().getConnectionCache().getConnectionError(c) : "OK") + "\n";
				}
				return ret;
			}
		});
		
		subcommands.put("users", new SubCommand() {
			public String parse(Scanner l) {
				// either all known users in all containers, or just one container
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						return getUsers(l.next());
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch(NoSuchElementException e) {
					// all containers
					return getUsers();
				}
			}
		});
		
		subcommands.put("certs", new SubCommand() {
			public String parse(Scanner l) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						return getCerts(l.next());
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch(NoSuchElementException e) {
					// all containers
					return getCerts();
				}
			}
		});
		
		// second field is the commands 
		secondField.addAll(subcommands.keySet());
	}

	//{"containers", "ams", "sms", "brokers", "actors", "clients", "users", "errors", "exportedResources", "certs", "packages"};
	
	public ShowCommand() {
		
	}
	
	public String getCommandHelp() {
		return readHelpFile();
	}

	public String getCommandName() {
		return COMMAND_NAME;
	}

	public String getCommandShortDescription() {
		return "Show the state of things";
	}

	public List<Completer> getCompleters() {
		List<Completer> ret = new LinkedList<Completer>();
		
		// construct the fourth completer out of active container urls and active actor names
		
		Collection<String> fourthCompleter = MainShell.getInstance().getConnectionCache().getContainers();
		Collection<ActorMng> actors = MainShell.getInstance().getConnectionCache().getActiveActors();
		
		List<String> actorNames = new LinkedList<String>();
		for (ActorMng a: actors) 
			actorNames.add(a.getName());
		
		fourthCompleter.addAll(actorNames);
		
		ret.add(new ArgumentCompleter(new StringsCompleter(COMMAND_NAME, MainShell.EXIT_COMMAND),
				new StringsCompleter(secondField),
				new StringsCompleter(thirdField),
				new StringsCompleter(fourthCompleter),
				new StringsCompleter(fifthField),
				new NullCompleter()
				));
		return ret;
	}

	public String parseLine(String l) {
		Scanner scanner = new Scanner(l);
		try {
			if (COMMAND_NAME.equals(scanner.next())) {
				// get the subcommand
				SubCommand s = subcommands.get(scanner.next());
				if (s == null)
					return syntaxError();
				String ret = s.parse(scanner);
				if (ret == null)
					return syntaxError();
				return ret;
			} else
				return syntaxError();
		} catch (NoSuchElementException e) {
			return syntaxError();
		}
	}

	public void shutdown() {
		// TODO Auto-generated method stub
	}
	
	private String syntaxError() {
		return "Syntax error.\n" + getCommandHelp();
	}

	/**
	 * Various static helpers to above
	 */
	
	
	/**
	 * Get users of all containers
	 * @return
	 */
	private static String getUsers() {
		String ret = "";
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				ret += getUsers(c);
		}
		return ret;
	}
	
	/**
	 * Get users (for a specific container; url can be null)
	 * @param url
	 */
	private static String getUsers(String url) {
		String ret = "";
		try {
			IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
			if (proxy == null)
				return "No connection to container " + url + "\n";
			List<UserMng> users = proxy.getUsers();
			for (UserMng u: users) {
				ret += "  " + u.getLogin() + " [" + u.getFirst() + ", " + u.getLast() + "] in " + url + "\n";
			}
			return ret;
		} catch (NoSuchElementException ee) {
			return null;
		}
	}
	
	private static String getCerts() {
		String ret = "";
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				ret += getCerts(c);
		}
		return ret;
	}
	
	private static String getCerts(String url) {
		try {
			IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
			if (proxy == null)
				return "No connection to container " + url + "\n";
			Certificate cert = proxy.getCertificate();
			return url + ":\n" + cert.toString();
		} catch (NoSuchElementException ee) {
			return null;
		}
	}
}
