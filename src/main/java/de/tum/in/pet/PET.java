package de.tum.in.pet;

import de.tum.in.pet.implementation.core.CoreChecker;
import de.tum.in.pet.implementation.reachability.ReachChecker;
import java.io.IOException;
import prism.PrismException;

@SuppressWarnings("PMD")
public final class PET {
  private PET() {}

  public static void main(String... args) throws IOException, PrismException {
    if (args.length < 1) {
      System.out.println("I need a sub-tool (reachability, core)");
      System.exit(1);
    }

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
