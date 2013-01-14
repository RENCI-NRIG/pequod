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
import orca.manage.IOrcaClientActor;
import orca.manage.IOrcaServerActor;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ReservationMng;
import orca.manage.beans.SliceMng;
import orca.pequod.main.MainShell;
import orca.shirako.common.ReservationID;
import orca.shirako.common.SliceID;

public class ManageCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="manage";
	private static String[] thirdField = {"reservation", "slice"};
	private static String[] fifthField = {"for"};
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
					if (!"on".equals(l.next()))
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
					if (!"reservation".equals(l.next())) {
						closeRes = true;
					} else { 						
						if (!"slice".equals(l.next())) {
							closeRes = false;
						} else 
							return null;
					}
					// res or slice id
					String id = l.next();
					if (!"on".equals(l.next()))
						return null;
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
					if (!"on".equals(l.next()))
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
		Collection<ActorMng> actors = MainShell.getInstance().getConnectionCache().getActiveActors();
		
		List<String> actorNames = new LinkedList<String>();
		for (ActorMng a: actors) 
			actorNames.add(a.getName());
		
		fourthCompleter.add(CURRENT);
		fourthCompleter.add(ALL);
		
		Collection<String>sixthCompleter = actorNames;
		
		Collection<String>eightthCompleter = actorNames;
		eightthCompleter.add(CURRENT);
		
		ret.add(new ArgumentCompleter(new StringsCompleter(COMMAND_NAME, MainShell.EXIT_COMMAND),
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
						break;
					}
				}
			}
			return ret;
		}
		
		return claimReservation2(rid, brokerActor, amActor);
	}
	
	private static String claimReservation2(String rid, IOrcaActor brokerActor, IOrcaActor amActor) {
		
		IOrcaClientActor brokerClientActor = null;
		try {
			brokerClientActor = (IOrcaClientActor)brokerActor;
		} catch (ClassCastException e) {
			return "ERROR: actor " + brokerActor.getName() + " is not a broker";
		}
		
		IOrcaServerActor amServerActor = null;
		try {
			amServerActor = (IOrcaServerActor)amActor;
		} catch (ClassCastException e) {
			return "ERROR: actor " + amActor.getName() + " us not an AM";
		}
		
		ReservationMng res = amActor.getReservation(new ReservationID(rid));
		
		if (res == null) 
			return "ERROR: reservation " + rid + " does not exist on " + amActor.getName();
		
		brokerClientActor.claimResources(amActor.getGuid(), new SliceID(res.getSliceID()), new ReservationID(rid));
		return "Claiming on " + brokerActor.getName() + " from actor " + amActor.getGuid() + " reservation " + rid + " slice " + res.getSliceID();
	}
	
	private static String closeReservation(String rid, String actorName) {
		
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
							ret += closeReservation(sliceId, a);
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
