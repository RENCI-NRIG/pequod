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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import orca.manage.IOrcaServiceManager;
import orca.manage.OrcaConverter;
import orca.manage.OrcaError;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ClientMng;
import orca.manage.beans.PackageMng;
import orca.manage.beans.PoolInfoMng;
import orca.manage.beans.PropertiesMng;
import orca.manage.beans.PropertyMng;
import orca.manage.beans.ReservationMng;
import orca.manage.beans.SliceMng;
import orca.manage.beans.TicketReservationMng;
import orca.manage.beans.UnitMng;
import orca.manage.beans.UserMng;
import orca.pequod.main.Constants;
import orca.pequod.main.MainShell;
import orca.shirako.common.ConfigurationException;
import orca.shirako.common.ReservationID;
import orca.shirako.common.SliceID;
import orca.shirako.common.meta.ResourcePoolAttributeDescriptor;
import orca.shirako.common.meta.ResourcePoolDescriptor;
import orca.shirako.common.meta.ResourceProperties;
import orca.util.ResourceType;

import org.apache.commons.lang.text.StrSubstitutor;

import edu.emory.mathcs.backport.java.util.Arrays;

public class ShowCommand extends CommandHelper implements ICommand {
	private static final String FILTER_VAL = "val";
	private static final String FILTER_KEY = "key";
	public static final String COMMAND_NAME="show";
	public static final int LOGCHUNKSIZE = 1024*32;
	private static String[] thirdField = {"for"};
	private static String[] fifthField = {"actor"};
	private static String[] seventhField = {"state", "type", "filter"};
	private static String[] eighthField = {
		Constants.PropertyType.LOCAL.getName(), 
		Constants.PropertyType.REQUEST.getName(), 
		Constants.PropertyType.RESOURCE.getName(), 
		Constants.PropertyType.CONFIGURATION.getName(),
		Constants.PropertyType.UNIT.getName(),
		Constants.ReservationState.ACTIVE.getName(),
		Constants.ReservationState.ACTIVETICKETED.getName(),
		Constants.ReservationState.CLOSED.getName(),
		Constants.ReservationState.CLOSEWAIT.getName(),
		Constants.ReservationState.FAILED.getName(),
		Constants.ReservationState.NASCENT.getName(),
		Constants.ReservationState.TICKETED.getName(),
		Constants.ReservationState.ALL.getName()};
	private static String[] ninthField = {"filter"};
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
				StringBuilder sb = new StringBuilder();
				// return a list of containers and their status
				List<String> showLast = new LinkedList<String>();
				for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
					showLast.add(c);
					sb.append("  " + c + "\t" + (MainShell.getInstance().getConnectionCache().isConnectionInError(c) ?
							MainShell.getInstance().getConnectionCache().getConnectionError(c) : "OK") + "\n");
				}
				MainShell.getInstance().getConnectionCache().setLastShowContainers(showLast);
				return sb.toString();
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
					String actor = l.next();
					try {
						if (!"filter".equals(l.next()))
							return null;
						String filter = null;
						try {
							filter = l.next();
						} catch (NoSuchElementException ee) {
							return null;
						}
						List<SliceMng> lm = new LinkedList<SliceMng>();
						String ret = getSlices(actor, lm, filter);
						if (ret == null)
							return null;
						MainShell.getInstance().getConnectionCache().setLastShowSlices(lm);
						ret += "\nTotal: " + lm.size() + " slices";
						return ret;
					} catch (NoSuchElementException e) {
						List<SliceMng> lm = new LinkedList<SliceMng>();
						String ret = getSlices(actor, lm, null);
						if (ret == null)
							return null;
						MainShell.getInstance().getConnectionCache().setLastShowSlices(lm);
						ret += "\nTotal: " + lm.size() + " slices";
						return ret;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("deadslices", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					String actor = l.next();

					List<SliceMng> lm = new LinkedList<SliceMng>();
					String ret = getDeadSlices(actor, lm);
					if (ret == null)
						return null;
					MainShell.getInstance().getConnectionCache().setLastShowSlices(lm);
					ret += "\nTotal: " + lm.size() + " slices";
					return ret;
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
						if (ret == null)
							return null;
						MainShell.getInstance().getConnectionCache().setLastShowSlices(lm);
						ret += "\nTotal: " + lm.size() + " slices";
						return ret;
					} catch (NoSuchElementException ee) {
						return null;
					}
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("available", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					String smName = l.next();
					if (!"actor".equals(l.next())) {
						return null;
					}
					String brokerName = l.next();
					try {
						if (!"filter".equals(l.next())) 
							return null;
						String filter = l.next();
						return getAvailableResources(smName, brokerName, filter);
					} catch (NoSuchElementException ee) {
						return getAvailableResources(smName, brokerName, null);
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
		
		subcommands.put("packages", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"for".equals(l.next())) {
						return null;
					}
					try {
						String url = l.next();
						return getContainerPackages(url);
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch (NoSuchElementException e) {
					// show errors on all actors
					return getAllContainerPackages();
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
						String stateName = l.next();
						try {
							if (!"filter".equals(l.next()))
								return null;
							String filter = l.next();
							String ret = getReservations(sliceId, actorName, Constants.ReservationState.getType(stateName), r, filter);
							if (ret == null)
								return null;
							MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
							ret += "\nTotal: " + r.size() + " reservations";
							return ret;
						} catch (NoSuchElementException e) {
							String ret = getReservations(sliceId, actorName, Constants.ReservationState.getType(stateName), r, null);
							if (ret == null)
								return null;
							MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
							ret += "\nTotal: " + r.size() + " reservations";
							return ret;
						}
					} catch (NoSuchElementException e) {
						String ret = getReservations(sliceId, actorName, Constants.ReservationState.ALL, r, null);
						if (ret == null)
							return null;
						MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
						ret += "\nTotal: " + r.size() + " reservations";
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
						Constants.PropertyType propType = null;
						try {
							propType = Constants.PropertyType.getType(l.next());
						} catch (NoSuchElementException ee) {
							return null;
						}
						try {
							if (!"filter".equals(l.next()))
								return null;
							String filter = null;
							try {
								filter = l.next();
							} catch (NoSuchElementException ee) {
								return null;
							}
							Map<String, String> filterMap = getFilterMap(filter);
							if (filterMap == null)
								return null;
							String ret = getReservationProperties(rid, actorName, propType, r, filterMap);
							if (ret == null)
								return null;
							MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
							ret += "\nTotal: " + r.size() + " reservations";
							return ret;
						} catch (NoSuchElementException ee) {
							String ret = getReservationProperties(rid, actorName, propType, r, null);
							if (ret == null)
								return null;
							MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
							ret += "\nTotal: " + r.size() + " reservations";
							return ret;
						}
					} catch (NoSuchElementException e) {
						String ret = getReservationProperties(rid, actorName, Constants.PropertyType.ALL, r, null);
						if (ret == null)
							return null;
						MainShell.getInstance().getConnectionCache().setLastShowReservations(r);
						ret += "\nTotal: " + r.size() + " reservations";
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
		
		// get default completers (e.g. exit, history)
		List<String> defComp = defaultCompleters();
		// add the command
		defComp.add(COMMAND_NAME);
		ret.add(new ArgumentCompleter(new StringsCompleter(defComp),
				new StringsCompleter(secondField),
				new StringsCompleter(thirdField),
				new StringsCompleter(fourthCompleter),
				new StringsCompleter(fifthField),
				new StringsCompleter(sixthCompleter),
				new StringsCompleter(seventhField),
				new StringsCompleter(eighthField),
				new StringsCompleter(ninthField),
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
		StringBuilder sb = new StringBuilder();
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				sb.append(getUsers(c));
		}
		return sb.toString();
	}
	
	/**
	 * Get users (for a specific container; url can be null)
	 * @param url
	 */
	private static String getUsers(String url) {
		StringBuilder sb = new StringBuilder();
		// NOTE: containers cannot be called current or all, so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						sb.append("Container " + u + ":\n");
						sb.append(getUsers(u));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current containers not set";
		}
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		if (proxy == null)
			return "ERROR: No connection to container " + url;
		List<UserMng> users = proxy.getUsers();
		for (UserMng u: users) {
			sb.append("  " + u.getLogin() + " [" + u.getFirst() + ", " + u.getLast() + "] in " + url + "\n");
		}
		return sb.toString();
	}
	
	private static String getCerts() {
		StringBuilder sb = new StringBuilder();
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				sb.append(getCerts(c));
		}
		return sb.toString();
	}
	
	private static String getCerts(String url) {
		StringBuilder sb = new StringBuilder();
		// NOTE: containers cannot be called current or all, so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						sb.append("Container " + u + ":\n");
						sb.append(getCerts(u));
					}
				}
				return sb.toString();
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
		StringBuilder sb = new StringBuilder();
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			if (!MainShell.getInstance().getConnectionCache().isConnectionInError(c))
				sb.append(getActorsByType(t, c, l));
		}
		return sb.toString();
	}
	
	/**
	 * Get actors from cache from specific container
	 * @param t
	 * @param url
	 * @return
	 */
	private static String getActorsByType(Constants.ActorType t, String url, List<ActorMng> l) {
		StringBuilder sb = new StringBuilder();

		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						sb.append("Container " + u + ":\n");
						sb.append(getActorsByType(t, u, l));
					}
				}
				return sb.toString();
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
			sb.append(a.getName() + "\t" + Constants.ActorType.getType(a.getType()).getName() +  "\t" + a.getID() + "\n");
			sb.append("\t" + MainShell.getInstance().getConnectionCache().getActorContainer(a.getName()) + "\n");
			sb.append("\t[" + a.getDescription() + "]\t " + "\n");
		}
		return sb.toString();
	}
	
	/**
	 * Get clients for authority or broker
	 * @param actorName
	 * @return
	 */
	private static String getClients(String actorName) {
		StringBuilder sb = new StringBuilder();

		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						sb.append("Actor " + a + ":\n");
						sb.append(getClients(a));
					}
				}
				return sb.toString();
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
			sb.append(c.getName() + "\t" + c.getGuid() + "\n");
		}	

		return sb.toString();
	}
	
	/**
	 * List slices in a particular actor
	 * @param actorName
	 * @param lm
	 * @param filter
	 * @return
	 */
	private static String getSlices(final String actorName, List<SliceMng> lm, String filter) {
		StringBuilder sb = new StringBuilder();
		String ffilter = null;
		
		if (filter != null) {
			if (!filter.startsWith("\"") || !filter.endsWith("\""))
				return null;
		
			ffilter = filter.substring(1, filter.length() - 1).trim();
		}
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						sb.append("Actor " + a + ":\n");
						sb.append(getSlices(a, lm, filter));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current actors not set";
		}
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		if (actor.getSlices() == null)
			return "ERROR: This actor has no slices";
		
		List<SliceMng> slices = actor.getSlices();
		List<SliceMng> matchSlices;
		if (ffilter != null) {
			matchSlices = new ArrayList<SliceMng>();
			for (SliceMng slice: slices) {
				if (slice.getName().contains(ffilter) || 
						slice.getName().matches(ffilter))
					matchSlices.add(slice);
			}
		} else
			matchSlices = slices;
		
		lm.addAll(matchSlices);
		
		for (SliceMng s: matchSlices)
			sb.append(s.getName() + "\t" + s.getSliceID() + "\t" + (s.getResourceType() != null ? s.getResourceType() : "") + "\n");
		return sb.toString();
	}
	
	/**
	 * List dead slices in a particular actor
	 * @param actorName
	 * @param lm
	 * @return
	 */
	private static String getDeadSlices(final String actorName, List<SliceMng> lm) {
		StringBuilder sb = new StringBuilder();

		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						sb.append("Actor " + a + ":\n");
						sb.append(getDeadSlices(a, lm));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current actors not set";
		}
		
		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: This actor does not exist";
		
		if (actor.getSlices() == null)
			return "ERROR: This actor has no slices";
		
		List<SliceMng> slices = actor.getSlices();
		List<SliceMng> matchSlices = new ArrayList<SliceMng>();

		// make sure all reservations are closed/failed
		for(SliceMng sl: slices) {
			List<ReservationMng> reservations = actor.getReservations(new SliceID(sl.getSliceID()));
			boolean keepSlice = true;
			for(ReservationMng rm: reservations) {
				if ((rm.getState() != Constants.ReservationState.CLOSED.getIndex()) && 
						(rm.getState() != Constants.ReservationState.FAILED.getIndex())) {
					keepSlice = false;
					break;
				}
			}
			if (keepSlice)
				matchSlices.add(sl);
		}
		lm.addAll(matchSlices);
		
		for (SliceMng s: matchSlices)
			sb.append(s.getName() + "\t" + s.getSliceID() + "\t" + (s.getResourceType() != null ? s.getResourceType() : "") + "\n");
		return sb.toString();
	}
	
	/**
	 * List inventory slices in a particular actor (with delegatable resources)
	 * @param actorName
	 * @return
	 */
	private static String getInventorySlices(final String actorName, List<SliceMng> lm) {
		StringBuilder sb = new StringBuilder();
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						sb.append("Actor " + a + ":\n");
						sb.append(getInventorySlices(a, lm));
					}
				}
				return sb.toString();
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
			sb.append(s.getName() + "\t" + s.getSliceID() + "\t" + (s.getResourceType() != null ? s.getResourceType() : "") + "\n");
		return sb.toString();
	}
	
	/** 
	 * get properties of a slice
	 * @param sliceName
	 * @param actorName
	 * @return
	 */
	private static String getSliceProperties(String sliceName, String actorName, Constants.PropertyType t) {
		StringBuilder sb = new StringBuilder();
		
		if (CURRENT.equals(sliceName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSliceIds() != null) {
				for (String s: MainShell.getInstance().getConnectionCache().getCurrentSliceIds()) {
					if (!CURRENT.equals(s)) {
						sb.append("Slice " + s + ":\n");
						sb.append(getSliceProperties(s, actorName, t));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current slice not set";
		}
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						sb.append(getSliceProperties(sliceName, a, t));
					}
				}
				return sb.toString();
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
			sb.append(t.getName().toUpperCase() + ":\n");
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
						sb.append(getSliceProperties(sliceName, actorName, pt));
					if (sb.toString().startsWith("ERROR"))
						return sb.toString();
				}
				return sb.toString();
			default:
					// no UNIT properties on slices
					return "";
			}
		}
		
		if (!fired)
			return "ERROR: No such slice  in this actor";
		
		for (PropertyMng p: props.getProperty()) {
			sb.append("\t" + p.getName() + " = " + p.getValue() + "\n");
		}
		
		return sb.toString();
	}
	
	/**
	 * Get last error of the container
	 * @param url
	 * @return
	 */
	private static String getContainerError(String url) {
		StringBuilder sb = new StringBuilder();
		// NOTE: containers cannot be called current so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						sb.append("Container " + u + ":\n");
						sb.append(getContainerError(u));
					}
				}
				return sb.toString();
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
		StringBuilder sb = new StringBuilder();
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
				for (String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
					if (!CURRENT.equals(a)) {
						sb.append("Actor " + a + ":\n");
						sb.append(getActorError(a));
					}
				}
				return sb.toString();
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
		StringBuilder sb = new StringBuilder();
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			sb.append(getContainerError(c) + "\n");
			for (ActorMng am: MainShell.getInstance().getConnectionCache().getActiveActors(c)) {
				sb.append("\t" + am.getName() + ":\t" + getActorError(am.getName()) + "\n");
			}
		}
		return sb.toString();
	}
	
	
	private static String getAllContainerPackages() {
		StringBuilder sb = new StringBuilder();
		for (String c: MainShell.getInstance().getConnectionCache().getContainers()) {
			sb.append("Container: " + c + "\n");
			sb.append(getContainerPackages(c));
		}
		return sb.toString();
	}
	
	private static String getContainerPackages(String url) {
		StringBuilder sb = new StringBuilder();
		// NOTE: containers cannot be called current so we're ok here	
		if (CURRENT.equals(url)) { 
			if (MainShell.getInstance().getConnectionCache().getCurrentContainers() != null) {
				for (String u: MainShell.getInstance().getConnectionCache().getCurrentContainers()) {
					if (!CURRENT.equals(u)) {
						sb.append("Container " + u + ":\n");
						sb.append(getContainerPackages(u));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current container not set";
		}
		
		IOrcaContainer proxy = MainShell.getInstance().getConnectionCache().getContainer(url);
		
		if (proxy == null)
			return "ERROR: No connection to container " + url;
		
		List<PackageMng> packages = proxy.getPackages();
		
		if (packages != null) {
			for (PackageMng p: packages) {
				sb.append(p.getName() + ":" + p.getId() + "\t" + p.getDescription() + "\n");
			}
			return sb.toString();
		}
		else 
			return "No packages";
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
		StringBuilder sb = new StringBuilder();
		for(String s: ret) {
			sb.append(s + "\n");
		}
		return sb.toString();
	}
	
	/**
	 * Get reservations from a particular slice (based on slice ID, not name)
	 * @param sliceID
	 * @param actorName
	 * @param s
	 * @return
	 */
	private static String getReservations(final String sliceId, final String actorName, final Constants.ReservationState s, 
			List<ReservationMng> rm, final String filter) {
		
		if (filter != null) {
			if (!filter.startsWith("\"") || !filter.endsWith("\""))
				return null;
		}
		
		StringBuilder sb = new StringBuilder();
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							sb.append("Actor " + a + ":\n");
							sb.append(getReservations(sliceId, a, s, rm, filter));
						}
					}
					return sb.toString();
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
						sb.append("Reservations for slice " + ss + ":\n");
						sb.append(getReservations2(ss, actor, s, rm, filter));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current slice not set";
		} else {
		
			if (ALL.equals(sliceId)) {
				if (actor.getSlices() == null)
					return "ERROR: This actor has no slices";
				for (SliceMng slice: actor.getSlices()) {
					// Note the shift from slice name to slice id
					sb.append(getReservations2(slice.getSliceID(), actor, s, rm, filter));
				}
				return sb.toString();
			}
		}
		
		return getReservations2(sliceId, actor, s, rm, filter);
	}
	
	/**
	 * To avoid recursion loops for 'all' slices. Note it uses slice ids, not names
	 * @param sliceId
	 * @param actor
	 * @param s
	 * @return
	 */
	private static String getReservations2(final String sliceId, final IOrcaActor actor, final Constants.ReservationState s, 
			List<ReservationMng> rm, final String filter) {
		StringBuilder sb = new StringBuilder();
		String ffilter = null;
		
		if (filter != null) {
			ffilter = filter.substring(1, filter.length() - 1).trim();
		}
		
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
		
		if (reservations != null) {
			for (ReservationMng res: reservations) {
				// only include reservations matching a property filter
				if ((ffilter != null) && (ffilter.length() > 0)) {
					if (!res.getResourceType().contains(ffilter) && !res.getResourceType().matches(ffilter))
						continue;
				}
				rm.add(res);
				sb.append(res.getReservationID() + "\t" + actor.getName() + "\n\t" + "Slice: " + sliceId + "\n\t" +
				res.getUnits() + "\t" + res.getResourceType() + "\t[ " + Constants.ReservationState.getState(res.getState()) +", " + 
				Constants.ReservationState.getState(res.getPendingState()) + "]\t\n");
				sb.append("\tNotices: " + res.getNotices().trim());
				if (!res.getNotices().trim().endsWith("\n")) 
					sb.append("\n");
				Date st = new Date(res.getStart());
				Date en = new Date(res.getEnd());
				Date reqEn = new Date(res.getRequestedEnd());
				sb.append("\tStart: " + st + "\tEnd: " + en + "\tRequested end: " + reqEn + "\n");

			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Parse the filter into a map
	 * @param filter
	 * @return
	 */
	private static Map<String, String> getFilterMap(String filter) {
		if (filter == null)
			return null;
		
		if (!filter.startsWith("\"") || !filter.endsWith("\""))
			return null;
		
		filter = filter.substring(1, filter.length() - 1).trim();
		
		String[] splits = filter.split("=");
		String pName = splits[0].trim();
		String pVal = null;
		if (splits.length == 2)
			pVal = splits[1].trim();

		if (splits.length > 2)
			return null;
		
		if ((pName.length() == 0) && ((pVal == null) || (pVal.length() == 0)))
			return null;
		
		HashMap<String, String> ret = new HashMap<String, String>();
		ret.put(FILTER_KEY, pName);
		ret.put(FILTER_VAL, pVal);

		return ret;
	}
	
	/**
	 * Get reservation properties for a particular reservation on a given actor
	 * @param rid
	 * @param actorName
	 * @param s
	 * @return
	 */
	private static String getReservationProperties(String rid, String actorName, Constants.PropertyType s, List<ReservationMng> rm, Map<String, String> filter) {
		StringBuilder sb = new StringBuilder();
		
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							sb.append("Actor " + a + ":\n");
							sb.append(getReservationProperties(rid, a, s, rm, filter));
						}
					}
					return sb.toString();
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
						sb.append("Reservation " + rrid + ":\n");
						sb.append(getReservationProperties2(rrid, actor, s, rm, filter));
					}
				}
				return sb.toString();
			}
			else
				return "ERROR: Current reservation not set";
		} 
		
		return getReservationProperties2(rid, actor, s, rm, filter);
	}
	
	private static List<PropertiesMng> getUnitProperties(String rid, IOrcaActor actor) {
		
		List<PropertiesMng> uProps = new LinkedList<PropertiesMng>();
		try {
			IOrcaServiceManager iosm = (IOrcaServiceManager)actor;
			List<UnitMng> units = iosm.getUnits(new ReservationID(rid));
			for(UnitMng unit: units) {
				PropertiesMng up = unit.getProperties();
				if (up != null)
					uProps.add(up);
			}
		} catch (ClassCastException e) {
			;
		} catch (Exception ee) {
			;
		}
		return uProps;
	}
	
	/**
	 * Get reservation properties
	 * @param rid
	 * @param actor
	 * @param s
	 * @return
	 */
	private static String getReservationProperties2(String rid, IOrcaActor actor, Constants.PropertyType s, List<ReservationMng> rm, Map<String, String> filter) {
		StringBuilder sb = new StringBuilder();
		
		ReservationMng reservation = actor.getReservation(new ReservationID(rid));
		
		if (reservation == null)
			return "ERROR: Reservation " + rid + " does not exist on actor " + actor.getName();
		
		PropertiesMng cProps = null;
		PropertiesMng rProps = null;
		PropertiesMng lProps = null;
		PropertiesMng rsProps = null;
		List<PropertiesMng> uProps = null;
		
		switch(s) {
		case ALL:
			cProps = reservation.getConfigurationProperties();
			rProps = reservation.getRequestProperties();
			lProps = reservation.getLocalProperties();
			rsProps = reservation.getResourceProperties();
			uProps = getUnitProperties(rid, actor);
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
		case UNIT:
			uProps = getUnitProperties(rid, actor);
			break;
		case UNKNOWN:
		default:
			return "ERROR: Unknown property type";
		}
		
		rm.add(reservation);
		
		sb.append(reservation.getReservationID() + "\n");

		if (cProps != null) {
			sb.append(printProperties(Constants.PropertyType.CONFIGURATION, cProps, filter));
		}
		
		if (lProps != null) {
			sb.append(printProperties(Constants.PropertyType.LOCAL, lProps, filter));
		}
		
		if (rProps != null) {
			sb.append(printProperties(Constants.PropertyType.REQUEST, rProps, filter));
		}
		
		if (rsProps != null) {
			sb.append(printProperties(Constants.PropertyType.RESOURCE, rsProps, filter));
		}	
		
		// get unit properties for this reservation
		if ((uProps != null) && (uProps.size() > 0)) {
			int i = 0;
			for (PropertiesMng pm: uProps) {
				sb.append(i++ + " ");
				sb.append(printProperties(Constants.PropertyType.UNIT, pm, filter));
			}
		}
		
		return sb.toString();
	}
	
	private static String printProperties(Constants.PropertyType tt, PropertiesMng props, Map<String, String> filter) {
		if (props == null)
			return null;

		String ret = tt.getName().toUpperCase() + ":\n";
		List<PropertyMng> l = props.getProperty();
		if (l != null) {
			for (PropertyMng pm: l) {
				if (filter == null)
					ret += "\t" + pm.getName() + " = " + pm.getValue() + "\n"; 
				else {
					boolean flag = false;
					if ((filter.get(FILTER_KEY) != null) && (filter.get(FILTER_VAL) != null))
						flag = dualMatch(pm, filter.get(FILTER_KEY), filter.get(FILTER_VAL));
					else if ((filter.get(FILTER_KEY) != null) || (filter.get(FILTER_VAL) != null))
						flag = singleMatch(pm, filter.get(FILTER_KEY));
					if (flag)
						ret += "\t" + pm.getName() + " = " + pm.getValue() + "\n"; 
				}
			}
		}
		return ret;
	}
	
	private static boolean dualMatch(PropertyMng pm, String key, String val) {
		if (pm.getName().equals(key) && pm.getValue().contains(val))
			return true;
		return false;
	}
	
	private static boolean singleMatch(PropertyMng pm, String filter) {
		if (pm.getName().contains(filter) || pm.getValue().contains(filter) || pm.getValue().matches(filter))
			return true;
		return false;
	}
	
	private static String printLogTail(int lines) {
		StringBuilder sb = new StringBuilder();
		
		String logFile = MainShell.getInstance().getProperty("log4j.appender.file.File");
		logFile = StrSubstitutor.replaceSystemProperties(logFile);
		
		if (logFile == null) 
			return "Unable to find logfile";
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(new File(logFile), "r");
			
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
					
				if (readLen < 0) {
					return "Unable to read logfile";
				}
					
			}
			
			InputStream bs = new ByteArrayInputStream(buf);
			InputStreamReader isr = new InputStreamReader(bs);
			BufferedReader br = new BufferedReader(isr);
			
			while (br.ready()) {
				sb.append(br.readLine() + "\n");
			}
	
			return sb.toString();
		} catch (FileNotFoundException e) {
			return "Unable to open logfile: " + e;
		} catch (IOException e) {
			return "Unable to read logfile: " + e;
		} finally {
			try {
				if (raf != null)
					raf.close();
			} catch (Exception io) {
				;
			}
		}
	}
	
	private static class ActorComparable implements Comparator<ActorMng> {

		@Override
		public int compare(ActorMng arg0, ActorMng arg1) {
			return arg0.getName().compareTo(arg1.getName());
		}
	}
	
	private static String getAvailableResources(String smName, String brokerName, String filter ) {
		String ffilter = null;
		
		if (filter != null) {
			if (!filter.startsWith("\"") || !filter.endsWith("\""))
				return null;
		
			ffilter = filter.substring(1, filter.length() - 1).trim();
		}
		
		IOrcaActor smActor = MainShell.getInstance().getConnectionCache().getOrcaActor(smName);
		IOrcaActor brokerActor = MainShell.getInstance().getConnectionCache().getOrcaActor(brokerName);
		
		if (smActor == null) 
			return "ERROR: Actor " + smName + " does not exist";
		
		if (brokerActor == null)
			return "ERROR: Actor " + brokerName +" does not exist";
		
		IOrcaServiceManager sm = null;
		try {
			sm = (IOrcaServiceManager)smActor;
		} catch (ClassCastException cce) {
			return "ERROR: Actor " + smName + " is not an SM";
		}
		List<PoolInfoMng> pools = sm.getPoolInfo(brokerActor.getGuid());
		if (pools == null) 
			return "ERROR: no resource pool information available";
		
		List<String> arPool = new ArrayList<String>();
		for(PoolInfoMng p: pools) {
			try {
				ResourcePoolDescriptor rpd = OrcaConverter.fill(p);
				ResourceType type = rpd.getResourceType();
				ResourcePoolAttributeDescriptor a = rpd.getAttribute(ResourceProperties.ResourceDomain);
	    		if (a == null) {
	    			return "ERROR: missing domain information for resource pool:  " + type;
	    		}
	    		String domain = a.getValue();
	    		a = rpd.getAttribute(ResourceProperties.ResourceAvailableUnits);
	    		int total = a.getIntValue();
	    		arPool.add(domain.replaceAll("/", ".") + " = " + total + "\n");
			} catch (ConfigurationException ce) {
				return "ERROR: configuration exception while retrieving resource pool information: " + ce;
			}
		}
		String[] tmp = arPool.toArray(new String[0]);
		Arrays.sort(tmp);
		StringBuilder sb = new StringBuilder("Resources available to " + smName + " from " + brokerName + "\n");
		for(String t: tmp) {
			if ((ffilter != null) && (ffilter.length() > 0)) {
				if (!t.contains(ffilter) && !t.matches(ffilter)) 
					continue;
			}
			sb.append("\tResource " + t);
		}
		return sb.toString();
	}
}
