package orca.pequod.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import orca.pequod.main.MainShell;

public class FileCommand extends CommandHelper implements ICommand {
	public static String COMMAND_NAME="file";
	
	private static final List<String> secondField = new LinkedList<String>();
	private static Map<String, SubCommand> subcommands = new HashMap<String, SubCommand>();
	
	static {
		subcommands.put("execute", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String fName = l.next();
					return executeFile(fName);
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		subcommands.put("show", new SubCommand() {
			public String parse(Scanner l, String last) {
				try {
					String fName = l.next();
					return showFile(fName);
				} catch (NoSuchElementException e) {
					return null;
				}
			}
		});
		
		// second field is the commands 
		secondField.addAll(subcommands.keySet());
	}
	
	public FileCommand() {
		mySubcommands = subcommands;
	}
	
	@Override
	public String getCommandName() {
		return COMMAND_NAME;
	}

	@Override
	public String getCommandShortDescription() {
		return "File-related commands";
	}

	@Override
	public List<Completer> getCompleters() {
		List<Completer> ret = new LinkedList<Completer>();
		
		ret.add(new ArgumentCompleter(new StringsCompleter(COMMAND_NAME, MainShell.EXIT_COMMAND),
				new StringsCompleter(secondField),
				new FileNameCompleter(),
				new NullCompleter()
				));
		return ret;
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
	}

	private static String executeFile(String fName) {
		String ret = "";
		try {
			FileReader fr = new FileReader(new File(fName));
			BufferedReader br = new BufferedReader(fr);
			
			while(br.ready()) {
				String tmp = br.readLine();
				if ((tmp != null) && (!tmp.startsWith("#")))
					ret += execLine(tmp);
			}
		} catch (FileNotFoundException e) {
			return "ERROR: unable to load file " + fName;
		} catch (IOException e) {
			return "ERROR: unable to read file " + fName;
		}
		return ret;
	}
	
	private static String execLine(String s) {
		Scanner scanner = new Scanner(s);
		String first = null;
		try {
			first = scanner.next().trim();
		} catch (NoSuchElementException e) {
			;
		}

		ICommand cmd = MainShell.getInstance().getCommands().get(first);
		if (cmd == null) {
			if (MainShell.EXIT_COMMAND.equals(first)) {
				return "";
			} else if (!"".equals(s))
				return "ERROR: Syntax error.";
			return "";
		} else {
			return cmd.parseLine(s);
		}
	}
	
	private static String showFile(String fName) {
		String ret = "";
		try {
			FileReader fr = new FileReader(new File(fName));
			BufferedReader br = new BufferedReader(fr);
			
			while(br.ready()) {
				ret += br.readLine() + "\n";
			}
		} catch (FileNotFoundException e) {
			return "ERROR: unable to load file " + fName;
		} catch (IOException e) {
			return "ERROR: unable to read file " + fName;
		}
		return ret;
	}
}
