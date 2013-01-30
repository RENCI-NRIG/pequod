package orca.pequod.commands;

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
	private static String[] seventhField = {"from"};
	
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
					if (!"reservation".equals(l.next())) {
						return null;
					}
					String rid = l.next();
					if (!"actor".equals(l.next()))
						return null;
					String actorName = l.next();
					return removeReservation(rid, actorName);
				} catch (NoSuchElementException e) {
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
		
		boolean res = actor.removeReservation(new ReservationID(rid));
		
		return "Removed reservation " + rid + " on " + actorName + " with result " + res;
	}
}
