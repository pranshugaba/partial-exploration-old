package de.tum.in.pet.util;

import de.tum.in.pet.sampler.SuccessorHeuristic;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@SuppressWarnings("PMD")
public final class CliHelper {
  private static final Logger logger = Logger.getLogger(CliHelper.class.getName());

  private CliHelper() {}

  public static Option getDefaultHeuristicOption() {
    return new Option(null, "heuristic", true, "Heuristic to use");
  }

  public static SuccessorHeuristic parseHeuristic(String optionString,
      SuccessorHeuristic defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }
    try {
      return SuccessorHeuristic.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse heuristic", e);
      String values = Arrays.stream(SuccessorHeuristic.values())
          .map(Object::toString)
          .collect(Collectors.joining(", "));
      System.out.println("Unknown heuristic " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static CommandLine parse(Options options, String[] args) {
    HelpFormatter formatter = new HelpFormatter();
    CommandLineParser cliParser = new DefaultParser();
    try {
      return cliParser.parse(options, args);
    } catch (ParseException e) {
      System.out.println("Failed to parse command line arguments: " + e.getMessage());
      formatter.printHelp("argument list", options);
      System.exit(1);
      throw new AssertionError(e);
    }
  }
}
