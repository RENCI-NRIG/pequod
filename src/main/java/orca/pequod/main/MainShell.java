package orca.pequod.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
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
import orca.pequod.commands.HelpCommand;
import orca.pequod.commands.ICommand;
import orca.pequod.util.PropertyLoader;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * Pequod is a shell environment for controlling ORCA actors
 * that supports multiple commands:
 * $ pequod
 * pequod> show [<options>]*
 * 
 * or
 * $ pequod show [<options>]*
 * 
 * Individual commands can be added as additional class plugins.
 * Configuration is contained in a properties file that enumerates
 * the known containers and actors and security tokens needed to
 * create security associations with them.
 * 
 * Basic commands are:
 * show [<subcommand>] [<paremeters>*] - show the environment
 * help
 * set
 * manage
 * 
 * Additional commands can be added at startup time using a properties file
 * that lists classes implementing individual commands
 * 
 * @author ibaldin
 */

public class MainShell {
	public static final String buildVersion = MainShell.class.getPackage().getImplementationVersion();
	public static final String aboutText = "Pequod ORCA Shell " + (buildVersion == null? "Eclipse build" : buildVersion) + " (c) 2012-2013 RENCI/UNC Chapel Hill " ;
	public static final String EXIT_COMMAND = "exit";
	public static final String HISTORY_COMMAND = "history";
	private static final String PEQUOD_DEAFULT_PROMPT_PROP = "pequod.default.prompt";
	private static final String PEQUOD_COMMANDS_PROP = "pequod.commands";
	private static final String PEQUOD_CONTAINERS_PROP = "pequod.containers";
	private static final String PEQUOD_USERNAME_PROP = "pequod.username";
	private static final String PEQUOD_PASSWORD_PROP = "pequod.password";
	private static final String PREF_DIR = ".pequod";
	private static final String PREF_FILE="properties";
	Logger logger = null;

	
	protected Map<String, ICommand> commands = new HashMap<String, ICommand>();
	protected Properties props;
	protected StringsCompleter topCompleter;
	protected ConsoleReader console;
	protected PrintWriter pw;
	protected String topPrompt;
	protected ICommand subCommand = null;
	protected ConnectionCache cc;
	
	private MainShell() {
		
		processPreferences();

		if (props.get(PEQUOD_COMMANDS_PROP) == null) {
			System.err.println("ERROR: Unable to determine the list of command classes, exiting.");
			System.exit(1);
		}

		if ((props.get(PEQUOD_CONTAINERS_PROP) == null)) {
			System.err.println("ERROR: Unable to determine the list of containers, exiting.");
			System.exit(1);
		}
		
		// logger 
		PropertyConfigurator.configure(props);
		logger = Logger.getLogger(orca.pequod.main.MainShell.class);
		if (logger == null)
			System.err.println("Unable to get logger");
		else
			logger.info("Logger initialized");
		
		// Connection cache
		String containers = (String)props.get(PEQUOD_CONTAINERS_PROP);
		String username = (String)props.get(PEQUOD_USERNAME_PROP);
		String password = (String)props.get(PEQUOD_PASSWORD_PROP);
		List<String> containerList = new LinkedList<String>();
		for (String s: containers.split(",")) {
			containerList.add(s.trim());
		}
	
		cc = new ConnectionCache(containerList, username, password, logger);
		
		// initialize command set
		String commandClasses = (String)props.get(PEQUOD_COMMANDS_PROP);
		initCommands(commandClasses.split(","));
		
		// Init terminal
		TerminalFactory.configure(TerminalFactory.AUTO);
		TerminalFactory.reset();
		
		// initialize the completer and console
		Set<String> cmdNames = new HashSet<String>(commands.keySet());
		cmdNames.add(EXIT_COMMAND);
		cmdNames.add(HISTORY_COMMAND);
		
		topCompleter = new StringsCompleter(cmdNames);
		try {
			console = new ConsoleReader();
			console.setHistoryEnabled(true);
			console.setPaginationEnabled(true);
			console.addCompleter(topCompleter);
			pw = new PrintWriter(console.getOutput());
			topPrompt = (String)props.get(PEQUOD_DEAFULT_PROMPT_PROP);
			console.setPrompt(topPrompt + ">");
		} catch(IOException ioe) {
			System.err.println("Unable to create ConsoleReader, exiting.");
			System.exit(1);
		}
	}
	
	private Properties loadProperties(String fileName) throws IOException {
		File prefs = new File(fileName);
		FileInputStream is = new FileInputStream(prefs);
		BufferedReader bin = new BufferedReader(new InputStreamReader(is, "UTF-8"));

		Properties p = new Properties();
		p.load(bin);
		bin.close();

		return p;
	}

	public String getProperty(String name) {
		return props.getProperty(name);
	}
	
    protected void processPreferences() {
    	// load default properties
    	props = PropertyLoader.loadProperties("orca/pequod/pequod.properties");

    	if (props == null) {
    		System.err.println("Unable to load default configuration properties. Fatal error, please contact developers. Exiting");
    		System.exit(1);
    	}
    	
    	Properties p = System.getProperties();

    	// load user properties and merge them with defaults
    	String prefFilePath = PREF_FILE;

    	prefFilePath = "" + p.getProperty("user.home") + p.getProperty("file.separator") + PREF_DIR + p.getProperty("file.separator") + PREF_FILE;
    	try {
    		Properties userProps = loadProperties(prefFilePath);
    		props.putAll(userProps);
    	} catch (IOException e) {
    		System.err.println("Unable to load local config file " + prefFilePath + ", exiting.");
    		InputStream is = Class.class.getResourceAsStream("/orca/pequod/pequod.sample.properties");
    		if (is != null) {
    			try {
    				String s = new java.util.Scanner(is).useDelimiter("\\A").next();
    				System.err.println("Create $HOME/.pequod/properties file as follows: \n\n" + s);
    			} catch (java.util.NoSuchElementException ee) {
    				;
    			}
    		} else {
    			System.err.println("Unable to load sample properties");
    		}
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
		String ret = aboutText + "\n\n";
		//ret += "Pequod supports history and command auto-completion.\nAvailable commands:\n";
		
		ret += getAllCommandsHelp();
		ret += "  history: show command history (!<command index> invokes the command)\n";
		ret += "  exit: Exit from the shell (Ctrl-D or Ctrl-C also works)\n";
		ret += "Type the entire command, or enter the first word of the command to enter subcommand with intelligent auto-completion (Using TAB).";
		ret += "\n\n\"It is not down on any map; true places never are.\"\n\t\t-Herman Melville, Moby Dick\n";
		pw.println(ret);
	}
	
	public String getAllCommandsHelp() {
		String ret = "";
		for (String cmd: commands.keySet()) {
			ret += "  " + cmd + ": " + commands.get(cmd).getCommandShortDescription() + "\n";
		}
		return ret;
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
			pw.println("\nLogging out of containers");
			pw.flush();
			cc.shutdown();
			pw.print("Shutting down commands ");
			pw.flush();
			for (Entry<String, ICommand> cmd: commands.entrySet()) {
				pw.print(cmd.getKey() + " ");
				pw.flush();
				cmd.getValue().shutdown();
			}
			pw.println("\nResetting terminal and exiting. Goodbye.");
			pw.flush();
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
	
	private String history_print() {
		StringBuilder sb = new StringBuilder();
				
		for (int i=0; i< console.getHistory().size(); i++) {
			sb.append(i + ": " + console.getHistory().get(i) + "\n");
		}
		return sb.toString();
	}
	
	public ICommand getSubCommand() {
		return subCommand;
	}
	
	protected String readLine() throws IOException {
		
		String s = null;
		
		try {
			s = console.readLine();
		} catch (IllegalArgumentException e) {
			console.setCursorPosition(0);
			console.killLine();
			console.beep();
			pw.println("ERROR: no command with this index");
			pw.flush();
			return s;
		} catch (Exception e) {
			console.setCursorPosition(0);
			console.killLine();
			console.beep();
			pw.println("ERROR: exception " + e);
			pw.flush();
			return s;
		}

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
				} else if (HISTORY_COMMAND.equals(first)) {
					pw.println(history_print());
				} else if (!"".equals(s)) {
					console.beep();
					pw.println("ERROR: Syntax error.");
				}
			}
			if (cmd != null) {
				try {
					if (cmd.getCommandName().equals(HelpCommand.COMMAND_NAME)) {
						pw.println(cmd.parseLine(s));
					} else {
						// is there anything following it?
						scanner.next();
						// execute command
						String ret = cmd.parseLine(s);
						pw.println(ret);
					}
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
	 * Returns a map of commands
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
