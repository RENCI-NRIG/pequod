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
import orca.manage.OrcaError;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ClientMng;
import orca.manage.beans.PropertiesMng;
import orca.manage.beans.PropertyMng;
import orca.manage.beans.SliceMng;
import orca.manage.beans.UserMng;
import orca.pequod.main.Constants;
import orca.pequod.main.MainShell;

public class ShowCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="show";
	private static String[] thirdField = {"for"};
	private static String[] fifthField = {"actor"};
	private static String[] seventhField = {"state", "type"};
	private static String[] eighthField = {Constants.PropertyType.LOCAL.getName(), 
		Constants.PropertyType.REQUEST.getName(), 
		Constants.PropertyType.RESOURCE.getName(), 
		Constants.PropertyType.CONFIGURATION.getName(),
		Constants.PropertyType.ALL.getName()};
	private static final String CURRENT = "current";
	private static final String ALL = "all";
	
	private static final List<String> secondField = new LinkedList<String>();
	private static Map<String, SubCommand> subcommands = new HashMap<String, SubCommand>();
	
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
					String sliceName = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String actorName = l.next();

					try {
						if (!"type".equals(l.next()))
							return null;

						String propType = l.next();

						return getSliceProperties(sliceName, actorName, Constants.PropertyType.getType(propType));
					} catch (NoSuchElementException e) {
						return getSliceProperties(sliceName, actorName, Constants.PropertyType.ALL);
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("errors", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						String urlOrActor = l.next();
						if (urlOrActor.startsWith("http") || urlOrActor.startsWith(CURRENT) || urlOrActor.startsWith(ALL))
							return getContainerError(urlOrActor);
						else
							return getActorError(urlOrActor);
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("current", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						String current = l.next();
						return getCurrentSetting(Constants.CurrentType.getType(current));
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("reservations", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next()))
						return null;
					String sliceName = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String actorName = l.next();
					
					try {
						if (!"state".equals(l.next()))
							return null;
						return getReservations(sliceName, actorName, Constants.ReservationState.getType(l.next()));
					} catch (NoSuchElementException e) {
						return getReservations(sliceName, actorName, Constants.ReservationState.ALL);
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
		mySubcommands = subcommands;
	}

	public String getCommandName() {
		return COMMAND_NAME;
	}

	public String getCommandShortDescription() {
		return "Show the state of things";
	}

	public List<Completer> getCompleters() {
		List<Completer> ret = new LinkedList<Completer>();
		
		// some completers are dynamically constructed
		
		Collection<String> fourthCompleter = MainShell.getInstance().getConnectionCache().getContainers();
		Collection<ActorMng> actors = MainShell.getInstance().getConnectionCache().getActiveActors();
		
		List<String> actorNames = new LinkedList<String>();
		for (ActorMng a: actors) 
			actorNames.add(a.getName());
		
		fourthCompleter.addAll(actorNames);
		fourthCompleter.add(Constants.CurrentType.CONTAINER.getName());
		fourthCompleter.add(Constants.CurrentType.ACTOR.getName());
		fourthCompleter.add(Constants.CurrentType.SLICE.getName());
		fourthCompleter.add(Constants.CurrentType.RESERVATION.getName());
		
		fourthCompleter.add(CURRENT);
		fourthCompleter.add(ALL);
		
		Collection<String>sixthCompleter = actorNames;
		sixthCompleter.add(CURRENT);
		
		ret.add(new ArgumentCompleter(new StringsCompleter(COMMAND_NAME, MainShell.EXIT_COMMAND),
				new StringsCompleter(secondField),
				new StringsCompleter(thirdField),
				new StringsCompleter(fourthCompleter),
				new StringsCompleter(fifthField),
				new StringsCompleter(sixthCompleter),
				new StringsCompleter(seventhField),
				new StringsCompleter(eighthField),
				new NullCompleter()
				));
		return ret;
	}

	public void shutdown() {
		// TODO Auto-generated method stub
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
		// NOTE: containers cannot be called current or all, so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainer() != null)
				url = MainShell.getInstance().getConnectionCache().getCurrentContainer();
			else
				return "ERROR: Current container not set";
		}
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url;
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
		// NOTE: containers cannot be called current or all, so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainer() != null)
				url = MainShell.getInstance().getConnectionCache().getCurrentContainer();
			else
				return "ERROR: Current container not set";
		}
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url;
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
		
		// NOTE: containers cannot be called current or all, so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainer() != null)
				url = MainShell.getInstance().getConnectionCache().getCurrentContainer();
			else
				return "ERROR: Current container not set";
		}
		
		if (ALL.equals(url)) 
			return getActorsByType(t);
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url;
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

		// NOTE: actor could be named 'current'. Then we're in trouble.
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActor() != null)
				actorName = MainShell.getInstance().getConnectionCache().getCurrentActor();
			else
				return "ERROR: Current actor not set";
		}
		
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
		// NOTE: actor could be named 'current'. Then we're in trouble.
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActor() != null)
				actorName = MainShell.getInstance().getConnectionCache().getCurrentActor();
			else
				return "ERROR: Current actor not set";
		}
		
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
	
	/** 
	 * get properties of a slice
	 * @param sliceName
	 * @param actorName
	 * @return
	 */
	private static String getSliceProperties(String sliceName, String actorName, Constants.PropertyType t) {
		String ret = "";
		
		// NOTE: slice can be named 'current' and we're in trouble
		if (CURRENT.equals(sliceName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSlice() != null)
				sliceName = MainShell.getInstance().getConnectionCache().getCurrentSlice();
			else
				return "ERROR: Current slice not set";
		}
		
		// NOTE: actor can be named 'current' and we're in trouble
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActor() != null)
				actorName = MainShell.getInstance().getConnectionCache().getCurrentActor();
			else
				return "ERROR: Current actor not set";
		}
		
		if (t == Constants.PropertyType.UNKNOWN)
			return null;
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		List<SliceMng> slices = actor.getSlices();

		if (slices == null)
			return "ERROR: This actor has no slices";
		
		PropertiesMng props = null;
		boolean fired = false;
		for(SliceMng slice: slices) {
			if (!slice.getName().equals(sliceName))
				continue;
			fired = true;
			switch(t) {
			case RESOURCE:
				ret += "Resource Properties:\n";
				props = slice.getResourceProperties();
				break;
			case REQUEST:
				ret += "Request Properties:\n";
				props = slice.getRequestProperties();
				break;
			case CONFIGURATION:
				ret += "Configuration Properties:\n";
				props = slice.getConfigurationProperties();
				break;
			case LOCAL:
				ret += "Local Properties:\n";
				props = slice.getLocalProperties();
				break;
			case ALL:
				ret += getSliceProperties(sliceName, actorName, Constants.PropertyType.RESOURCE);
				if (ret.startsWith("ERROR"))
					return ret;
				ret += getSliceProperties(sliceName, actorName, Constants.PropertyType.REQUEST);
				ret += getSliceProperties(sliceName, actorName, Constants.PropertyType.CONFIGURATION);
				ret += getSliceProperties(sliceName, actorName, Constants.PropertyType.LOCAL);
				return ret;
			}
		}
		if (!fired)
			return "ERROR: No such slice in this actor";
	
		for (PropertyMng p: props.getProperty()) {
			ret += p.getName() + "\t" + p.getValue() + "\n";
		}
		
		return ret;
	}
	
	/**
	 * Get last error of the container
	 * @param url
	 * @return
	 */
	private static String getContainerError(String url) {
		// NOTE: containers cannot be called current so we're ok here	
		
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainer() != null)
				url = MainShell.getInstance().getConnectionCache().getCurrentContainer();
			else
				return "ERROR: Current container not set";
		}
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		
		if (proxy == null)
			return "ERROR: No connection to container " + url;
		
		OrcaError err = proxy.getLastError();
		if (err != null)
			return err.toString();
		else 
			return "No errors";
	}
	
	/**
	 * Get last error of the actor
	 * @param name
	 * @return
	 */
	private static String getActorError(String actorName) {
		// NOTE: actor can be named 'current' and we're in trouble
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActor() != null)
				actorName = MainShell.getInstance().getConnectionCache().getCurrentActor();
			else
				return "ERROR: Current actor not set";
		}
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		OrcaError err = actor.getLastError();
		
		if (err != null)
			return err.toString();
		return "No errors";
	}
	
	/**
	 * Get the value of current setting
	 * @param s
	 * @return
	 */
	private static String getCurrentSetting(Constants.CurrentType t) {
		String ret = null;
		switch(t) {
		case CONTAINER:
			ret = MainShell.getInstance().getConnectionCache().getCurrentContainer();
			break;
		case ACTOR:
			ret = MainShell.getInstance().getConnectionCache().getCurrentActor();
			break;
		case SLICE:
			ret = MainShell.getInstance().getConnectionCache().getCurrentSlice();
			break;
		case RESERVATION:
			ret = MainShell.getInstance().getConnectionCache().getCurrentReservation();
			break;
		case UNKNOWN:
			return null;
		}
		if (ret == null)
			return "";
		return ret;
	}
	
	/**
	 * Get reservations from a particular slice
	 * @param sliceName
	 * @param actorName
	 * @param s
	 * @return
	 */
	private static String getReservations(String sliceName, String actorName, Constants.ReservationState s) {
		// NOTE: slice can be named 'current' and we're in trouble
		if (CURRENT.equals(sliceName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSlice() != null)
				sliceName = MainShell.getInstance().getConnectionCache().getCurrentSlice();
			else
				return "ERROR: Current slice not set";
		}

		// NOTE: slice can be named 'all' and we're in trouble
		if (ALL.equals(sliceName)) {
			
		}
		
		// NOTE: actor can be named 'current' and we're in trouble
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActor() != null)
				actorName = MainShell.getInstance().getConnectionCache().getCurrentActor();
			else
				return "ERROR: Current actor not set";
		}
		
		
		return null;
	}
}
