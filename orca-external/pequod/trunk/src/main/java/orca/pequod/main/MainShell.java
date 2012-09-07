package orca.pequod.main;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;

import jline.TerminalFactory;
import jline.console.ConsoleReader;
import jline.console.completer.Completer;
import jline.console.completer.StringsCompleter;
import orca.pequod.commands.ICommand;
import orca.pequod.util.PropertyLoader;

import org.apache.log4j.PropertyConfigurator;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * Pequod works similar to openssl - you can execute individual
 * commands (e.g. openssl x509 <options>) or enter into a shell
 * environment and do it from there:
 * $ openssl
 * openssl> x509 <options>
 * 
 * Individual commands can be added as additional class plugins.
 * Configuration is contained in a properties file that enumerates
 * the known containers and actors and security tokens needed to
 * create security associations with them.
 * 
 * Basic commands are:
 * show [<subcommand>] [<paremeters>*] - show the environment
 * ping <container> - check container liveness
 * help
 * 
 * @author ibaldin
 *
 */

public class MainShell {
	public static final String EXIT_COMMAND = "exit";
	private static final String PEQUOD_DEAFULT_PROMPT_PROP = "pequod.default.prompt";
	private static final String PEQUOD_COMMANDS_PROP = "pequod.commands";
	private static final String PEQUOD_CONTAINERS_PROP = "pequod.containers";
	private static final String PEQUOD_USERNAME_PROP = "pequod.username";
	private static final String PEQUOD_PASSWORD_PROP = "pequod.password";
	
	protected Map<String, ICommand> commands = new HashMap<String, ICommand>();
	protected Properties props;
	protected StringsCompleter topCompleter;
	protected ConsoleReader console;
	protected PrintWriter pw;
	protected String topPrompt;
	protected ICommand subCommand = null;
	protected ConnectionCache cc;
	
	private MainShell() {
		// construct a list of known commands
		props = PropertyLoader.loadProperties("orca.pequod.pequod.properties");
		
		if (props == null) {
			System.err.println("ERROR: Unable to load default properties, exiting.");
			System.exit(1);
		}
		if (props.get(PEQUOD_COMMANDS_PROP) == null) {
			System.err.println("ERROR: Unable to determine the list of command classes, exiting.");
			System.exit(1);
		}

		if ((props.get(PEQUOD_CONTAINERS_PROP) == null) ||
				(props.get(PEQUOD_USERNAME_PROP) == null) ||
				(props.get(PEQUOD_PASSWORD_PROP) == null)) {
			System.err.println("ERROR: Unable to determine the list of containers, exiting.");
			System.exit(1);
		}
		
		// logger 
		PropertyConfigurator.configure(props);
		
		// Connection cache
		String containers = (String)props.get(PEQUOD_CONTAINERS_PROP);
		String username = (String)props.get(PEQUOD_USERNAME_PROP);
		String password = (String)props.get(PEQUOD_PASSWORD_PROP);
		List<String> containerList = new LinkedList<String>();
		for (String s: containers.split(",")) {
			containerList.add(s.trim());
		}
		
		cc = new ConnectionCache(containerList, username, password);
		
		// initialize command set
		String commandClasses = (String)props.get(PEQUOD_COMMANDS_PROP);
		initCommands(commandClasses.split(","));
		
		// Init terminal
		TerminalFactory.configure(TerminalFactory.AUTO);
		TerminalFactory.reset();
		
		// initialize the completer and console
		Set<String> cmdNames = new HashSet<String>(commands.keySet());
		cmdNames.add(EXIT_COMMAND);
		
		topCompleter = new StringsCompleter(cmdNames);
		try {
			console = new ConsoleReader();
			console.addCompleter(topCompleter);
			pw = new PrintWriter(console.getOutput());
			topPrompt = (String)props.get(PEQUOD_DEAFULT_PROMPT_PROP);
			console.setPrompt(topPrompt + ">");
		} catch(IOException ioe) {
			System.err.println("Unable to create ConsoleReader, exiting.");
			System.exit(1);
		}
	}
	
	private static MainShell instance = new MainShell();
	public static MainShell getInstance() {
		return instance;
	}
	
	@SuppressWarnings("unchecked")
	private void initCommands(String[] commandClasses) {
		for (String cName: commandClasses) {
			try {
				cName = cName.trim();
				Class<?> clazz = Class.forName(cName, true, MainShell.class.getClassLoader());
				ICommand ic = (ICommand)clazz.newInstance();
				if (ic.getCommandName() == null) 
					System.err.println("ERROR: Unable to add class " + cName + " because command name is null");
				else
					commands.put(ic.getCommandName(), ic);
			} catch (ClassNotFoundException cnfe) {
				System.err.println("ERROR: Unable to find class " + cName + ", continuing.");
			} catch (InstantiationException ie) {
				System.err.println("ERROR: Unable to instantiate class " + cName + ", continuing.");
			} catch (Exception e) {
				System.err.println("ERROR: Unable to create instance of " + cName + " due to " + e + ", continuing");
			}			
		}
		commands = Collections.unmodifiableMap(commands);
	}
	
	private void printHelp() {
		// collect help messages from the commands
		String ret = "Pequod Orca Shell (c) 2012 RENCI/UNC Chapel Hill\nPequod supports history and command auto-completion.\nAvailable commands:\n";
		
		for (String cmd: commands.keySet()) {
			ret += "  " + cmd + ": " + commands.get(cmd).getCommandShortDescription() + "\n";
		}
		ret += "  exit: Exit from the shell (Ctrl-D or Ctrl-C also works)\n";
		
		ret += "Type the entire command, or enter the first word of the command to enter subcommand with intelligent auto-completion (Using TAB).";
		pw.println(ret);
	}
	
	/**
	 * Do intelligent things on exit
	 */
	protected void addShutDownHandler() {
		Runtime.getRuntime().addShutdownHook(new Thread () {
			@Override
			public void run() {
				shutdownActions();
			}
		});
	}
	
	protected void shutdownActions() {
		try {
			System.out.println("\nLogging out of containers");
			cc.shutdown();
			System.out.print("Shutting down commands ");
			for (Entry<String, ICommand> cmd: commands.entrySet()) {
				System.out.print(cmd.getKey() + " ");
				cmd.getValue().shutdown();
			}
			System.out.println("\nResetting terminal and exiting. Goodbye.");
			TerminalFactory.get().restore();
			TerminalFactory.reset();
		} catch (Exception e) {
			System.err.println("ERROR: Unable to reset terminal" + e);
			e.printStackTrace();
		}
	}
	
	private void upOrExit() {
		if (subCommand != null) {
			Collection<Completer> cs = new HashSet<Completer>(console.getCompleters());
			
			for (Completer c: cs) 
				console.removeCompleter(c);

			console.addCompleter(topCompleter);
			console.setPrompt(topPrompt + ">");
			subCommand = null;
		} else
			System.exit(0);
			
	}
	
	public ICommand getSubCommand() {
		return subCommand;
	}
	
	protected String readLine() throws IOException {
		
		String s = console.readLine();
		if (s != null) {
			// parse it out
			Scanner scanner = new Scanner(s);
			String first = null;
			try {
				first = scanner.next().trim();
			} catch (NoSuchElementException e) {
				;
			}
			ICommand cmd = commands.get(first);
			if (cmd == null) {
				if (EXIT_COMMAND.equals(first)) {
					// either exit or pop one level up
					upOrExit();
				} else if (!"".equals(s))
					pw.println("ERROR: Syntax error.");
			}
			if (cmd != null) {
				try {
					// is there anything following it?
					scanner.next();
					// execute command
					String ret = cmd.parseLine(s);
					pw.println(ret);
				} catch (NoSuchElementException e) {
					// enter subcommand
					if (!cmd.equals(subCommand)) {
						subCommand = cmd;
						console.setPrompt(topPrompt + ":" + cmd.getCommandName() + ">");
						// replace the completer
						console.removeCompleter(topCompleter);
						Collection<Completer> l = cmd.getCompleters();
						if (l != null)
							for (Completer c: l) 
								console.addCompleter(c);
					} else {
						String ret = cmd.parseLine(s);
						pw.println(ret);
					}
				}
			}
		} else {
			// exit or pop one level up
			if (subCommand != null)
				pw.println();
			upOrExit();
		}
		pw.flush();
		return s;
	}
	
	public ConnectionCache getConnectionCache() {
		return cc;
	}
	
	/**
	 * Returns an unmodifiable map of commands
	 * @return
	 */
	public Map<String, ICommand> getCommands() {
		return commands;
	}
	
	public static void main(String[] argv) {

		MainShell ms = MainShell.getInstance();
		
		ms.addShutDownHandler();
		
		ms.printHelp();
		
		try {
			while(true)
				ms.readLine();
		} catch(IOException ioe) {
			;
		}
	}
}
