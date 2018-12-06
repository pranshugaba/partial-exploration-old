package de.tum.in.pet;

import de.tum.in.pet.implementation.core.CoreChecker;
import de.tum.in.pet.implementation.reachability.ReachChecker;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import prism.PrismException;

@SuppressWarnings("PMD")
public final class PET {
  private static final Logger logger = Logger.getLogger(PET.class.getName());

  private PET() {}

  public static void main(String... args) throws IOException, PrismException {
    if (args.length < 1) {
      System.out.println("I need a sub-tool (reachability, core)");
      System.exit(1);
    }

    logger.log(Level.INFO, "Invocation:\n{0}", String.join(" ", args));

    // TODO Ugly :-)
    String[] subArgs = new String[args.length - 1];
    System.arraycopy(args, 1, subArgs, 0, subArgs.length);

    switch (args[0]) {
      case "reachability":
        ReachChecker.main(subArgs);
        break;
      case "core":
        CoreChecker.main(subArgs);
        break;
      default:
        System.out.println("Unknown tool " + args[0]);
        System.exit(1);
        break;
    }
  }
}
