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
import orca.manage.IOrcaActor;
import orca.manage.IOrcaAuthority;
import orca.manage.IOrcaBroker;
import orca.manage.IOrcaContainer;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ClientMng;
import orca.manage.beans.PropertiesMng;
import orca.manage.beans.SliceMng;
import orca.manage.beans.UserMng;
import orca.pequod.main.Constants;
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
			public String parse(Scanner l, String last) {
				String ret = "";
				// return a list of containers and their status
				for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
					ret += "  " + c + "\t" + (MainShell.getInstance().getConnectionCache().isConnectionInError(c) ?
							MainShell.getInstance().getConnectionCache().getConnectionError(c) : "OK") + "\n";
				}
				return ret;
			}
		});
		
		subcommands.put("users", new SubCommand() {
			public String parse(Scanner l, String last) {
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
			public String parse(Scanner l, String last) {
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
		
		SubCommand showActors = new ShowActors();
		subcommands.put(Constants.ActorType.AM.getPluralName(), showActors);
		subcommands.put(Constants.ActorType.SM.getPluralName(), showActors);
		subcommands.put(Constants.ActorType.BROKER.getPluralName(), showActors);
		subcommands.put(Constants.ActorType.ACTOR.getPluralName(), showActors);
		
		subcommands.put("clients", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						return getClients(l.next());
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("slices", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						return getSlices(l.next());
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("sliceProperties", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						String sliceName = l.next();
						if (!"actor".equals(l.next()))
							return null;
						String actorName = l.next();
						return getSliceProperties(sliceName, actorName);
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		// second field is the commands 
		secondField.addAll(subcommands.keySet());
	}
	
	/**
	 * Show ams, brokers, sms or all actors in some or all containers
	 * @author ibaldin
	 *
	 */
	static class ShowActors implements SubCommand {
		
		@Override
		public String parse(Scanner l, String last) {
			Constants.ActorType tp = Constants.ActorType.getType(last);
			
			try {
				if (!"for".equals(l.next())) {
					return null;
				}
				try {
					return getActorsByType(tp, l.next());
				} catch (NoSuchElementException e) {
					return null;
				}
			} catch (NoSuchElementException e) {
				return getActorsByType(tp);
			}
		}
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
				String scLast = scanner.next();
				SubCommand s = subcommands.get(scLast);
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

	public void shutdown() {
		// TODO Auto-generated method stub
	}
	
	private String syntaxError() {
		return "ERROR: Syntax error.\n" + getCommandHelp();
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
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url + "\n";
		List<UserMng> users = proxy.getUsers();
		for (UserMng u: users) {
			ret += "  " + u.getLogin() + " [" + u.getFirst() + ", " + u.getLast() + "] in " + url + "\n";
		}
		return ret;
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
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url + "\n";
		Certificate cert = proxy.getCertificate();
		return url + ":\n" + cert.toString();
	}
	
	/**
	 * Get actors from cache for all containers
	 * @param t
	 * @return
	 */
	private static String getActorsByType(Constants.ActorType t) {
		String ret = "";
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				ret += getActorsByType(t, c);
		}
		return ret;
	}
	
	/**
	 * Get actors from cache from specific container
	 * @param t
	 * @param url
	 * @return
	 */
	private static String getActorsByType(Constants.ActorType t, String url) {
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "No connection to container " + url + "\n";
		Collection<ActorMng> actors;
		switch(t) {
		case AM: actors = MainShell.getInstance().getConnectionCache().getActiveActors(Constants.ActorType.AM); break;
		case SM: actors = MainShell.getInstance().getConnectionCache().getActiveActors(Constants.ActorType.SM); break;
		case BROKER: actors = MainShell.getInstance().getConnectionCache().getActiveActors(Constants.ActorType.BROKER); break;
		default: actors = MainShell.getInstance().getConnectionCache().getActiveActors(); break;
		}
		String ret = "";
		for (ActorMng a: actors) {
			ret += a.getName() + "\t" + Constants.ActorType.getType(a.getType()).getName() + 
			"\t[" + a.getDescription() + "]\t " + 
			MainShell.getInstance().getConnectionCache().getActorContainer(a.getName()) + "\n";
		}
		return ret;
	}
	
	/**
	 * Get clients for authority or broker
	 * @param actorName
	 * @return
	 */
	private static String getClients(String actorName) {
		String ret = "";

		List<ClientMng> clients;

		IOrcaAuthority auth = MainShell.getInstance().getConnectionCache().getAuthority(actorName);
		if (auth != null) {
			if (auth.getClients() == null) {
				return "ERROR: This authority or broker has no clients.";
			}
			clients = auth.getClients();
		} else {
			IOrcaBroker broker = MainShell.getInstance().getConnectionCache().getBroker(actorName);
			if (broker != null) {
				if (broker.getClients() == null)
					return "ERROR: This authority or broker has no clients.";
				else
					clients = broker.getClients();
			} else {
				return "ERROR: No such authority or broker";
			}
		}

		for (ClientMng c: clients) {
			ret += c.getName() + "\t" + c.getGuid() + "\n";
		}	

		return ret;
	}
	
	/**
	 * List slices in a particular actor
	 * @param actorName
	 * @return
	 */
	private static String getSlices(String actorName) {
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		String ret = "";
		if (actor.getSlices() == null)
			return "ERROR: This actor has no slices";
		for (SliceMng s: actor.getSlices())
			ret += s.getName() + "\t" + s.getSliceID() + "\t" + (s.getResourceType() != null ? s.getResourceType() : "") + "\n";
		return ret;
	}
	
	private static String getSliceProperties(String sliceName, String actorName) {
		String ret = "";
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		
		List<SliceMng> slices = actor.getSlices();

		if (slices == null)
			return "ERROR: This actor has no slices";
		
		for(SliceMng slice: slices) {
			PropertiesMng pm = slice.getConfigurationProperties();
			PropertiesMng lm = slice.getLocalProperties();
			PropertiesMng reqm = slice.getRequestProperties();
			PropertiesMng resm = slice.getResourceProperties();
			
			
		}
		
		return ret;
	}
}
