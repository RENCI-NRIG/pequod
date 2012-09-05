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
import orca.manage.beans.ActorMng;
import orca.pequod.main.Constants;
import orca.pequod.main.MainShell;

public class SetCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="set";
	private static final List<String> secondField = new LinkedList<String>();
	private static String[] thirdField = { "container", "actor", "slice", "reservation" };
	private static Map<String, SubCommand> subcommands = new HashMap<String, SubCommand>();
	
	static {
		subcommands.put("current", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String curType = l.next();
					try {
						return setCurrent(l.next(), Constants.CurrentType.getType(curType));
					} catch (NoSuchElementException e) {
						return null;
					}
				} catch(NoSuchElementException e) {
					// all containers
					return null;
				}
			}
		});
		// second field is the commands 
		secondField.addAll(subcommands.keySet());
	}
	
	public SetCommand() {
		mySubcommands = subcommands;
	}
	
	@Override
	public String getCommandName() {
		return COMMAND_NAME;
	}

	@Override
	public String getCommandShortDescription() {
		return "Modify internal set variables";
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
		
		fourthCompleter.addAll(actorNames);
		fourthCompleter.add("slice name (no autocompletion)");
		fourthCompleter.add("reservation name (no autocompletion)");
		
		ret.add(new ArgumentCompleter(new StringsCompleter(COMMAND_NAME, MainShell.EXIT_COMMAND),
				new StringsCompleter(secondField),
				new StringsCompleter(thirdField),
				new StringsCompleter(fourthCompleter),
				new NullCompleter()
				));
		return ret;
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub

	}
	
	/**
	 * Set something of a current type (container, actor, slice, reservation)
	 * @param val
	 * @param t
	 * @return
	 */
	private static String setCurrent(String val, Constants.CurrentType t) {
		
		switch(t) {
		case CONTAINER:
			MainShell.getInstance().getConnectionCache().setCurrentContainer(val);
			break;
		case ACTOR:
			MainShell.getInstance().getConnectionCache().setCurrentActor(val);
			break;
		case SLICE:
			MainShell.getInstance().getConnectionCache().setCurrentSlice(val);
			break;
		case RESERVATION:
			MainShell.getInstance().getConnectionCache().setCurrentReservation(val);
			break;
		case UNKNOWN:
			return null;
		}
		return "OK";
	}

}
