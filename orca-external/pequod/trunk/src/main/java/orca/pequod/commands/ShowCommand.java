package orca.pequod.commands;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Date;
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
import orca.manage.IOrcaServerActor;
import orca.manage.OrcaError;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ClientMng;
import orca.manage.beans.PropertiesMng;
import orca.manage.beans.PropertyMng;
import orca.manage.beans.ReservationMng;
import orca.manage.beans.SliceMng;
import orca.manage.beans.UserMng;
import orca.pequod.main.Constants;
import orca.pequod.main.MainShell;
import orca.shirako.common.ReservationID;
import orca.shirako.common.SliceID;

import org.apache.commons.lang.text.StrSubstitutor;

public class ShowCommand extends CommandHelper implements ICommand {
	public static final String COMMAND_NAME="show";
	public static final int LOGCHUNKSIZE = 1024*32;
	private static String[] thirdField = {"for"};
	private static String[] fifthField = {"actor"};
	private static String[] seventhField = {"state", "type"};
	private static String[] eighthField = {Constants.PropertyType.LOCAL.getName(), 
		Constants.PropertyType.REQUEST.getName(), 
		Constants.PropertyType.RESOURCE.getName(), 
		Constants.PropertyType.CONFIGURATION.getName(),
		Constants.ReservationState.ACTIVE.getName(),
		Constants.ReservationState.ACTIVETICKETED.getName(),
		Constants.ReservationState.CLOSED.getName(),
		Constants.ReservationState.CLOSEWAIT.getName(),
		Constants.ReservationState.FAILED.getName(),
		Constants.ReservationState.NASCENT.getName(),
		Constants.ReservationState.TICKETED.getName()};
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
				List<String> showLast = new LinkedList<String>();
				for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
					showLast.add(c);
					ret += "  " + c + "\t" + (MainShell.getInstance().getConnectionCache().isConnectionInError(c) ?
							MainShell.getInstance().getConnectionCache().getConnectionError(c) : "OK") + "\n";
				}
				MainShell.getInstance().getConnectionCache().setLastShowContainers(showLast);
				return ret;
			}
		});
		
		subcommands.put("users", new SubCommand() {
			public String parse(Scanner l, String last) {
				// either all known users in < containers, or just one container
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
		
		subcommands.put("logs", new SubCommand() {
			public String parse(Scanner l, String last) {
				return printLogTail(0);				
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
						List<SliceMng> lm = new LinkedList<SliceMng>();
						String ret = getSlices(l.next(), lm);
						MainShell.getInstance().getConnectionCache().setLastShowSlices(lm);
						return ret;
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("inventory", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						List<SliceMng> lm = new LinkedList<SliceMng>();
						String ret = getInventorySlices(l.next(), lm);
						MainShell.getInstance().getConnectionCache().setLastShowSlices(lm);
						return ret;
					} catch (NoSuchElementException ee) {
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
					// show errors on all actors
					return getAllActorErrors();
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
					String sliceId = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String actorName = l.next();
					
					List<ReservationMng> r = new LinkedList<ReservationMng>();
					try {
						if (!"state".equals(l.next()))
							return null;
						String ret = getReservations(sliceId, actorName, Constants.ReservationState.getType(l.next()), r);
						MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
						return ret;
					} catch (NoSuchElementException e) {
						String ret = getReservations(sliceId, actorName, Constants.ReservationState.ALL, r);
						MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
						return ret;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("reservationProperties", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next()))
						return null;
					String rid = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String actorName = l.next();
					
					List<ReservationMng> r = new LinkedList<ReservationMng>();
					try {
						if (!"type".equals(l.next()))
							return null;
						String ret = getReservationProperties(rid, actorName, Constants.PropertyType.getType(l.next()), r);
						MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
						return ret;
					} catch (NoSuchElementException e) {
						String ret = getReservationProperties(rid, actorName, Constants.PropertyType.ALL, r);
						MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
						return ret;
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

			List<ActorMng> am = new LinkedList<ActorMng>();
			try {
				if (!"for".equals(l.next())) {
					return null;
				}
				try {
					String ret = getActorsByType(tp, l.next(), am);
					MainShell.getInstance().getConnectionCache().setLastShowActors(am);
					return ret;
				} catch (NoSuchElementException e) {
					return null;
				}
			} catch (NoSuchElementException e) {
				String ret = getActorsByType(tp, am);
				MainShell.getInstance().getConnectionCache().setLastShowActors(am);
				return ret;
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
		Collection<ActorMng> actors = MainShell.getInstance().getConnectionCache().getActiveActors(null);
		
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
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						ret += "Container " + u + ":\n";
						ret += getUsers(u);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current containers not set";
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
		String ret = "";
		// NOTE: containers cannot be called current or all, so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						ret += "Container " + u + ":\n";
						ret += getCerts(u);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current containers not set";
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
	private static String getActorsByType(Constants.ActorType t, List<ActorMng> l) {
		String ret = "";
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				ret += getActorsByType(t, c, l);
		}
		return ret;
	}
	
	/**
	 * Get actors from cache from specific container
	 * @param t
	 * @param url
	 * @return
	 */
	private static String getActorsByType(Constants.ActorType t, String url, List<ActorMng> l) {
		String ret = "";

		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						ret += "Container " + u + ":\n";
						ret += getActorsByType(t, u, l);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current containers not set";
		}
		
		if (ALL.equals(url)) 
			return getActorsByType(t, l);
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url;
		Collection<ActorMng> actors;
		switch(t) {
		case AM: actors = MainShell.getInstance().getConnectionCache().getActiveActors(Constants.ActorType.AM, url); break;
		case SM: actors = MainShell.getInstance().getConnectionCache().getActiveActors(Constants.ActorType.SM, url); break;
		case BROKER: actors = MainShell.getInstance().getConnectionCache().getActiveActors(Constants.ActorType.BROKER, url); break;
		default: actors = MainShell.getInstance().getConnectionCache().getActiveActors(url); break;
		}
		l.addAll(actors);
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

		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						ret += "Actor " + a + ":\n";
						ret += getClients(a);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current actors not set";
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
	private static String getSlices(String actorName, List<SliceMng> lm) {
		String ret = "";
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						ret += "Actor " + a + ":\n";
						ret += getSlices(a, lm);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current actors not set";
		}
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		if (actor.getSlices() == null)
			return "ERROR: This actor has no slices";
		
		lm.addAll(actor.getSlices());
		
		for (SliceMng s: actor.getSlices())
			ret += s.getName() + "\t" + s.getSliceID() + "\t" + (s.getResourceType() != null ? s.getResourceType() : "") + "\n";
		return ret;
	}
	
	/**
	 * List inventory slices in a particular actor (with delegatable resources)
	 * @param actorName
	 * @return
	 */
	private static String getInventorySlices(String actorName, List<SliceMng> lm) {
		String ret = "";
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						ret += "Actor " + a + ":\n";
						ret += getSlices(a, lm);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current actors not set";
		}
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		IOrcaServerActor sActor = null;
		
		try {
			sActor = (IOrcaServerActor)actor;
		} catch (ClassCastException e) {
			;
		}
		if (sActor == null)
			return "ERROR: actor " + actorName + " cannot have inventory";

		if (sActor.getInventorySlices() == null) 
			return "ERROR: actor " + actorName + " has no inventory";
		
		lm.addAll(sActor.getInventorySlices());
		
		for (SliceMng s: lm)
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
		
		if (CURRENT.equals(sliceName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSliceIds() != null) {
				for (String s: MainShell.getInstance().getConnectionCache().getCurrentSliceIds()) {
					if (!CURRENT.equals(s)) {
						ret += "Slice " + s + ":\n";
						ret += getSliceProperties(s, actorName, t);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current slice not set";
		}
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						ret += getSliceProperties(sliceName, a, t);
					}
				}
				return ret;
			}
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
			if ((!slice.getName().equals(sliceName)) && (!slice.getSliceID().toString().equals(sliceName)))
				continue;
			fired = true;
			ret += t.getName().toUpperCase() + ":\n";
			switch(t) {
			case RESOURCE:
				props = slice.getResourceProperties();
				break;
			case REQUEST:
				props = slice.getRequestProperties();
				break;
			case CONFIGURATION:
				props = slice.getConfigurationProperties();
				break;
			case LOCAL:
				props = slice.getLocalProperties();
				break;
			case ALL:
				for (Constants.PropertyType pt: Constants.PropertyType.values()) {
					if ((!pt.equals(Constants.PropertyType.UNKNOWN)) && 
							(!pt.equals(Constants.PropertyType.ALL)))
						ret += getSliceProperties(sliceName, actorName, pt);
					if (ret.startsWith("ERROR"))
						return ret;
				}
				return ret;
			}
		}
		
		if (!fired)
			return "ERROR: No such slice  in this actor";
	
		for (PropertyMng p: props.getProperty()) {
			ret += "\t" + p.getName() + " = " + p.getValue() + "\n";
		}
		
		return ret;
	}
	
	/**
	 * Get last error of the container
	 * @param url
	 * @return
	 */
	private static String getContainerError(String url) {
		String ret = "";
		// NOTE: containers cannot be called current so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						ret += "Container " + u + ":\n";
						ret += getContainerError(u);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current container not set";
		}
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		
		if (proxy == null)
			return "ERROR: No connection to container " + url;
		
		OrcaError err = proxy.getLastError();
		if (err != null)
			return url + ":\t" + err.toString();
		else 
			return "No errors";
	}
	
	/**
	 * Get last error of the actor
	 * @param name
	 * @return
	 */
	private static String getActorError(String actorName) {
		String ret = "";
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						ret += "Actor " + a + ":\n";
						ret += getActorError(a);
					}
				}
				return ret;
			}
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
	
	private static String getAllActorErrors() {
		String ret = "";
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			ret += getContainerError(c) + "\n";
			for (ActorMng am: MainShell.getInstance().getConnectionCache().getActiveActors(c)) {
				ret += "\t" + am.getName() + ":\t" + getActorError(am.getName()) + "\n";
			}
		}
		return ret;
	}
	
	/**
	 * Get the value of current setting
	 * @param s
	 * @return
	 */
	private static String getCurrentSetting(Constants.CurrentType t) {
		List<String> ret = null;
		switch(t) {
		case CONTAINER:
			ret = MainShell.getInstance().getConnectionCache().getCurrentContainers();
			break;
		case ACTOR:
			ret = MainShell.getInstance().getConnectionCache().getCurrentActors();
			break;
		case SLICE:
			ret = MainShell.getInstance().getConnectionCache().getCurrentSliceIds();
			break;
		case RESERVATION:
			ret = MainShell.getInstance().getConnectionCache().getCurrentReservationIds();
			break;
		case UNKNOWN:
			return null;
		}
		if (ret == null)
			return "";
		String r = "";
		for(String s: ret) {
			r += s + "\n";
		}
		return r;
	}
	
	/**
	 * Get reservations from a particular slice (based on slice ID, not name)
	 * @param sliceID
	 * @param actorName
	 * @param s
	 * @return
	 */
	private static String getReservations(String sliceId, String actorName, Constants.ReservationState s, List<ReservationMng> rm) {
		String ret = "";
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							ret += "Actor " + a + ":\n";
							ret += getReservations(sliceId, a, s, rm);
						}
					}
					return ret;
				}
			else
				return "ERROR: Current actor not set";
		}

		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";

		if (CURRENT.equals(sliceId)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSliceIds() != null) {
				for (String ss: MainShell.getInstance().getConnectionCache().getCurrentSliceIds()) {
					if (!CURRENT.equals(ss)) {
						ret += "Resevations for slice " + ss + ":\n";
						ret += getReservations2(ss, actor, s, rm);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current slice not set";
		} else {
		
			if (ALL.equals(sliceId)) {
				if (actor.getSlices() == null)
					return "ERROR: This actor has no slices";
				for (SliceMng slice: actor.getSlices()) {
					// Note the shift from slice name to slice id
					ret += getReservations2(slice.getSliceID(), actor, s, rm);
				}
				return ret;
			}
		}
		
		return getReservations2(sliceId, actor, s, rm);
	}
	
	/**
	 * To avoid recursion loops for 'all' slices. Note it uses slice ids, not names
	 * @param sliceId
	 * @param actor
	 * @param s
	 * @return
	 */
	private static String getReservations2(String sliceId, IOrcaActor actor, Constants.ReservationState s, List<ReservationMng> rm) {
		String ret = "";
		
		List<ReservationMng> reservations = null;
		
		switch(s) {
		case UNKNOWN:
			return "ERROR: Unknown state";
		case ALL:
			// try GUID first
			reservations = actor.getReservations(new SliceID(sliceId));
			if ((reservations == null) || (reservations.size() == 0)) {
				// try to convert slice name to slice GUID
			}
				
			break;
		default:
			// try GUID first
			reservations = actor.getReservations(new SliceID(sliceId), s.getIndex());
			if ((reservations == null) || (reservations.size() == 0)) {
				// try to convert slice name to slice GUID
			}
			break;
		}
		
		rm.addAll(reservations);
		
		for (ReservationMng res: reservations) {
			ret += res.getReservationID() + "\t" + actor.getName() + "\n\t" + 
			res.getUnits() + "\t" + res.getResourceType() + "\t[ " + Constants.ReservationState.getState(res.getState()) +", " + 
			Constants.ReservationState.getState(res.getPendingState()) + "]\t\n";
			ret += "\tNotices: " + res.getNotices().trim();
			if (!res.getNotices().trim().endsWith("\n")) 
				ret += "\n";
			Date st = new Date(res.getStart());
			Date en = new Date(res.getEnd());
			ret += "\tStart: " + st + "\tEnd:" + en;
		}
		
		return ret;
	}
	
	/**
	 * Get reservation properties for a particular reservation on a given actor
	 * @param rid
	 * @param actorName
	 * @param s
	 * @return
	 */
	private static String getReservationProperties(String rid, String actorName, Constants.PropertyType s, List<ReservationMng> rm) {
		String ret = "";
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							ret += "Actor " + a + ":\n";
							ret += getReservationProperties(rid, a, s, rm);
						}
					}
					return ret;
				}
			else
				return "ERROR: Current actor not set";
		}

		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";

		if (CURRENT.equals(rid)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentReservationIds() != null) {
				for (String rrid: MainShell.getInstance().getConnectionCache().getCurrentReservationIds()) {
					if (!CURRENT.equals(rrid)) {
						ret += "Reservation " + rrid + ":\n";
						ret += getReservationProperties2(rrid, actor, s, rm);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current reservation not set";
		} 
		
		return getReservationProperties2(rid, actor, s, rm);
	}
	
	/**
	 * Get reservation properties
	 * @param rid
	 * @param actor
	 * @param s
	 * @return
	 */
	private static String getReservationProperties2(String rid, IOrcaActor actor, Constants.PropertyType s, List<ReservationMng> rm) {
		String ret = "";
		
		ReservationMng reservation = actor.getReservation(new ReservationID(rid));
		
		if (reservation == null)
			return "ERROR: Reservation " + rid + " does not exist on actor " + actor.getName();
		
		PropertiesMng cProps = null;
		PropertiesMng rProps = null;
		PropertiesMng lProps = null;
		PropertiesMng rsProps = null;
		
		switch(s) {
		case ALL:
			cProps = reservation.getConfigurationProperties();
			rProps = reservation.getRequestProperties();
			lProps = reservation.getLocalProperties();
			rsProps = reservation.getResourceProperties();
			break;
		case CONFIGURATION:
			cProps = reservation.getConfigurationProperties();
			break;
		case LOCAL:
			lProps = reservation.getLocalProperties();
			break;
		case RESOURCE:
			rsProps = reservation.getResourceProperties();
			break;
		case REQUEST:
			rProps = reservation.getRequestProperties();
			break;
		case UNKNOWN:
		default:
			return "ERROR: Unknown property type";
		}

		ret += reservation.getReservationID() + "\n";

		if (cProps != null) {
			ret += printProperties(Constants.PropertyType.CONFIGURATION, cProps);
		}
		
		if (lProps != null) {
			ret += printProperties(Constants.PropertyType.LOCAL, lProps);
		}
		
		if (rProps != null) {
			ret += printProperties(Constants.PropertyType.REQUEST, rProps);
		}
		
		if (rsProps != null) {
			ret += printProperties(Constants.PropertyType.RESOURCE, rsProps);
		}	
		
		return ret;
	}
	
	private static String printProperties(Constants.PropertyType tt, PropertiesMng props) {
		if (props == null)
			return null;

		String ret = tt.getName().toUpperCase() + ":\n";
		List<PropertyMng> l = props.getProperty();
		if (l != null) {
			for (PropertyMng pm: l) {
				ret += "\t" + pm.getName() + " = " + pm.getValue() + "\n"; 
			}
		}
		return ret;
	}
	
	private static String printLogTail(int lines) {
		String ret = "";
		
		String logFile = MainShell.getInstance().getProperty("log4j.appender.file.File");
		logFile = StrSubstitutor.replaceSystemProperties(logFile);
		
		if (logFile == null) 
			return "Unable to find logfile";
		try {
			RandomAccessFile raf = new RandomAccessFile(new File(logFile), "r");
			
			// read a chunk of file
			final int chunkSize = LOGCHUNKSIZE;
			byte[] buf = new byte[chunkSize];
			
			boolean cont=true;
			while(cont) {
				cont = false;
				
				long end = raf.length();
			
				long start = end - chunkSize;
			
				if (start < 0)
					start = 0;
				else 
					raf.seek(start);
			
				long readLen = raf.read(buf, 0, (int)(end - start));
					
				if (readLen < 0)
					return "Unable to read logfile";
					
			}
			
			InputStream bs = new ByteArrayInputStream(buf);
			InputStreamReader isr = new InputStreamReader(bs);
			BufferedReader br = new BufferedReader(isr);
			
			while (br.ready()) {
				ret += br.readLine() + "\n";
			}
	
			return ret;
		} catch (FileNotFoundException e) {
			return "Unable to open logfile: " + e;
		} catch (IOException e) {
			return "Unable to read logfile: " + e;
		}
	}
}
