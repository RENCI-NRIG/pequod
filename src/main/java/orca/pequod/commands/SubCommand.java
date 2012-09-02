package orca.pequod.commands;

import java.util.Scanner;

/**
 * Subcommand functor interface
 * @author ibaldin
 *
 */
public interface SubCommand {
	String parse(Scanner l);
}
