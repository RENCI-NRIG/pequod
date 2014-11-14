package orca.pequod.commands;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import orca.manage.IOrcaServiceManager;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ReservationMng;
import orca.manage.beans.SliceMng;
import orca.pequod.main.MainShell;
import orca.shirako.common.ReservationID;
import orca.shirako.common.SliceID;

public class ManageCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="manage";
	private static String[] thirdField = {"reservation", "slice"};
	private static String[] fifthField = {"actor"};
	private static String[] seventhField = {"from", "date"};
	
	private static final String CURRENT = "current";
	private static final String ALL = "all";
	
	private static final List<String> secondField = new LinkedList<String>();
	private static Map<String, SubCommand> subcommands = new HashMap<String, SubCommand>();
	
	static {
		subcommands.put("claim", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"reservation".equals(l.next())) {
						return null;
					}
					String rid = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String brokerName = l.next();
					if (!"from".equals(l.next()))
						return null;
					String amName = l.next();
					return claimReservation(rid, brokerName, amName);
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("close", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					boolean closeRes = true;
					String tmp = l.next();
					if ("reservation".equals(tmp)) {
						closeRes = true;
					} else { 						
						if ("slice".equals(tmp)) {
							closeRes = false;
						} else 
							return null;
					}
					// res or slice id
					String id = l.next();
					if (!"actor".equals(l.next())) {
						return null;
					}
					String actorName = l.next();
					if (closeRes)
						return closeReservation(id, actorName);
					else
						return closeSlice(id, actorName);
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("remove", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String object = l.next();
					if ((!"reservation".equals(object)) &&
							(!"slice".equals(object))){
						return null;
					}
					String ridOrSlice = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String actorName = l.next();
					if ("reservation".equals(object))
						return removeReservation(ridOrSlice, actorName);
					else 
						return removeSlice(ridOrSlice, actorName);
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("extend", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					if (!"reservation".equals(l.next())) {
						return null;
					}
					String rid = l.next();
					if (!"actor".equals(l.next())) {
						return null;
					}
					String actorName = l.next();
					if (!"date".equals(l.next())) {
						return null;
					}
					
					StringBuilder date = new StringBuilder();
					date.append(l.next());
					if (!date.toString().startsWith("\""))
						return null;
					while(true) {
						date.append(" ");
						date.append(l.next());
						if (date.toString().endsWith("\""))
							break;
					}
					return extendReservation(rid, actorName, date.toString());
				} catch(NoSuchElementException e) {
					return null;
				}
			}
		});
		
		// second field is the commands 
		secondField.addAll(subcommands.keySet());
	}
	
	@Override
	public String getCommandName() {
		return COMMAND_NAME;
	}

	@Override
	public String getCommandShortDescription() {
		return "Manage actor state";
	}

	public ManageCommand() {
		mySubcommands = subcommands;
	}
	
	@Override
	public List<Completer> getCompleters() {
		List<Completer> ret = new LinkedList<Completer>();
		
		// some completers are dynamically constructed
		
		Collection<String> fourthCompleter = MainShell.getInstance().getConnectionCache().getContainers();
		Collection<ActorMng> actors = MainShell.getInstance().getConnectionCache().getActiveActors(null);
		
		List<String> actorNames = new LinkedList<String>();
		for (ActorMng a: actors) 
			actorNames.add(a.getName());
		
		fourthCompleter.add(CURRENT);
		fourthCompleter.add(ALL);
		
		Collection<String>sixthCompleter = actorNames;
		
		Collection<String>eightthCompleter = actorNames;
		eightthCompleter.add(CURRENT);
		
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
				new StringsCompleter(eightthCompleter),
				new NullCompleter()
				));
		return ret;
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
	}

	private static String claimReservation(String rid, String brokerName, String amName) {
		
		String ret = "";
		
		IOrcaActor brokerActor = MainShell.getInstance().getConnectionCache().getOrcaActor(brokerName);
		if (brokerActor == null)
			return "ERROR: Broker " + brokerName + " does not exist";
		
		if (CURRENT.equals(amName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							ret += "Actor: " + a + "\n";
							ret += claimReservation(rid, brokerName, a);
						}
					}
					return ret;
				}
			else
				return "ERROR: Current actor not set";
		}

		IOrcaActor amActor = MainShell.getInstance().getConnectionCache().getOrcaActor(amName);
		if (amActor == null)
			return "ERROR: Actor " + amName + " does not exist";
		
		if (CURRENT.equals(rid)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentReservationIds() != null) {
				for (String rrid: MainShell.getInstance().getConnectionCache().getCurrentReservationIds()) {
					if (!CURRENT.equals(rrid)) {
						ret += "Reservation: " + rrid + "\n";
						ret += claimReservation2(rrid, brokerActor, amActor);
					}
				}
				return ret;
			}
			else
				return "ERROR: Current slice not set";
		} else if (ALL.equals(rid)) {
			List<SliceMng> amSlices = amActor.getSlices(); 
			if (amSlices != null) {
				for (SliceMng s: amSlices) {
					if ((s.getName() != null) && (s.getName().equals(brokerName.trim()))) {
						List<ReservationMng> rr = amActor.getReservations(new SliceID(s.getSliceID()));
						if (rr != null) {
							for (ReservationMng res: rr) {
								ret += claimReservation2(res.getReservationID(), brokerActor, amActor) + "\n";
							}
						}
					}
				}
			}
			return ret;
		}
		
		return claimReservation2(rid, brokerActor, amActor);
	}
	
	private static String claimReservation2(String rid, IOrcaActor brokerActor, IOrcaActor amActor) {
		
		IOrcaBroker broker = null;
		try {
			broker = (IOrcaBroker)brokerActor;
		} catch (ClassCastException e) {
			return "ERROR: actor " + brokerActor.getName() + " is not a broker";
		}
		
		IOrcaAuthority amServerActor = null;
		try {
			amServerActor = (IOrcaAuthority)amActor;
		} catch (ClassCastException e) {
			return "ERROR: actor " + amActor.getName() + " us not an AM";
		}
		
		ReservationMng res = amActor.getReservation(new ReservationID(rid));
		
		if (res == null) 
			return "ERROR: reservation " + rid + " does not exist on " + amActor.getName();
		
		ReservationMng res1 = broker.claimResources(amActor.getGuid(), new ReservationID(rid));
		String ret = "";
		if (res1 != null) {
			ret = res1.getReservationID() + " " + res1.getState() + " " + res1.getSliceID() + " " + res1.getUnits();
		}
		return "Claiming on " + brokerActor.getName() + " from actor " + amActor.getGuid() + " reservation " + rid + " slice " + res.getSliceID() + 
		"[" + ret + "]";
	}
	
	private static String closeReservation(String rid, String actorName) {
		
		String ret = "";
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							ret += "Actor: " + a + "\n";
							ret += closeReservation(rid, a) + "\n";
						}
					}
					return ret;
				}
			else
				return "ERROR: Current actor not set";
		}

		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: Actor " + actorName + " does not exist";
		
		if (CURRENT.equals(rid)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentReservationIds() != null) {
				for (String rrid: MainShell.getInstance().getConnectionCache().getCurrentReservationIds()) {
					if (!CURRENT.equals(rrid)) {
						boolean res = actor.closeReservation(new ReservationID(rrid));
						ret += "Closed reservation " + rrid + " on " + actorName + " with result " + res + "\n";
					}
				}
				return ret;
			}
			else
				return "ERROR: Current reservation not set";
		} 
		
		boolean res = actor.closeReservation(new ReservationID(rid));
		return "Closed reservation " + rid + " on " + actorName + " with result " + res;
	}
	
	private static String closeSlice(String sliceId, String actorName) {
		
		String ret = "";
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							ret += "Actor: " + a + "\n";
							ret += closeReservation(sliceId, a) + "\n";
						}
					}
					return ret;
				}
			else
				return "ERROR: Current actor not set";
		}

		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: Actor " + actorName + " does not exist";

		if (CURRENT.equals(sliceId)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSliceIds() != null) {
				for (String slice: MainShell.getInstance().getConnectionCache().getCurrentSliceIds()) {
					if (!CURRENT.equals(slice)) {
						boolean res = actor.closeReservations(new SliceID(slice));
						ret += "Closed slice " + slice + " on " + actorName + " with result " + res + "\n";
					}
				}
				return ret;
			}
			else
				return "ERROR: Current slice not set";
		}
		boolean res = actor.closeReservations(new SliceID(sliceId));
		
		return "Closed slice " + sliceId + " on " + actorName + " with result " + res;
	}
	
	private static String removeReservation(String rid, String actorName) {
		
		String ret = "";
		if (CURRENT.equals(actorName)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null)
				if (MainShell.getInstance().getConnectionCache().getCurrentActors() != null) {
					for(String a: MainShell.getInstance().getConnectionCache().getCurrentActors()) {
						if (!CURRENT.equals(a)) {
							ret += "Actor: " + a + "\n";
							ret += closeReservation(rid, a);
						}
					}
					return ret;
				}
			else
				return "ERROR: Current actor not set";
		}

		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: Actor " + actorName + " does not exist";
		
		if (CURRENT.equals(rid)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentReservationIds() != null) {
				for (String rrid: MainShell.getInstance().getConnectionCache().getCurrentReservationIds()) {
					if (!CURRENT.equals(rrid)) {
						boolean res = actor.removeReservation(new ReservationID(rrid));
						ret += "Removed reservation " + rrid + " on " + actorName + " with result " + res + "\n";
					}
				}
				return ret;
			}
			else
				return "ERROR: Current reservation not set";
		} 
		
		boolean res = actor.removeReservation(new ReservationID(rid));
		
		return "Removed reservation " + rid + " on " + actorName + " with result " + res;
	}
	
	private static String removeSlice(String sliceId, String actorName) {
		
		String ret = "";

		IOrcaActor actor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (actor == null)
			return "ERROR: Actor " + actorName + " does not exist";
		
		if (CURRENT.equals(sliceId)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentSliceIds() != null) {
				for (String sid: MainShell.getInstance().getConnectionCache().getCurrentSliceIds()) {
					if (!CURRENT.equals(sid)) {
						boolean res = actor.removeSlice(new SliceID(sid));
						ret += "Removed slice " + sid + " on " + actorName + " with result " + res + "\n";
					}
				}
				return ret;
			}
			else
				return "ERROR: Current reservation not set";
		} 
		
		boolean res = actor.removeSlice(new SliceID(sliceId));
		
		return "Removed slice " + sliceId + " on " + actorName + " with result " + res;
	}
	
	
	private static String[] dateFormats = { "yyyy-MM-dd HH:mm", "MM/dd/yyyy HH:mm", "MMM d, yyyy HH:mm" };
	
	private static String extendReservation(String rid, String actorName, String date) {	
		IOrcaActor smActor = MainShell.getInstance().getConnectionCache().getOrcaActor(actorName);
		if (smActor == null) 
			return "ERROR: Actor " + actorName + " does not exist";
		IOrcaServiceManager sm = null;
		try {
			sm = (IOrcaServiceManager)smActor;
		} catch (ClassCastException cce) {
			return "ERROR: Actor " + actorName + " is not an SM. Extending reservation can only be done on an SM";
		}
		
		Date dateAsDate = null;
		for(String format: dateFormats) {
			SimpleDateFormat sdf1 = new SimpleDateFormat(format);
			try {
				dateAsDate = sdf1.parse(date.replaceAll("\"", ""));
				break;
			} catch(ParseException pe) {
				;
			}
		}
		if (dateAsDate == null)
			return "ERROR: Unable to parse date " + date;
		
		String ret = "";
		if (CURRENT.equals(rid)) {
			if (MainShell.getInstance().getConnectionCache().getCurrentReservationIds() != null) {
				for (String rrid: MainShell.getInstance().getConnectionCache().getCurrentReservationIds()) {
					if (!CURRENT.equals(rrid)) {
						ReservationMng rMng = sm.getReservation(new ReservationID(rrid));
						Date curEnd = new Date(rMng.getEnd());
						if (dateAsDate.before(curEnd))
							return "ERROR: new end date is earlier than current end date";
						boolean res = sm.extendReservation(new ReservationID(rrid), dateAsDate);
						ret += "Extended reservation " + rrid + " on " + actorName + " with result " + res + "\n";
					}
				}
				return ret;
			}
			else
				return "ERROR: Current reservation not set";
		}
		
		ReservationMng rMng = sm.getReservation(new ReservationID(rid));
		Date curEnd = new Date(rMng.getEnd());
		if (dateAsDate.before(curEnd))
			return "ERROR: new end date is earlier than current end date";
		boolean res = sm.extendReservation(new ReservationID(rid), dateAsDate);
		return "Extended reservation " + rid + " on " + actorName + " until " + dateAsDate + " with result " + res;
	}
}
