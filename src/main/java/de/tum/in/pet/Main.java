package de.tum.in.pet;

import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.RoaringNatBitSetFactory;
import de.tum.in.pet.Converter.PETConverter;
import de.tum.in.pet.implementation.core.CoreChecker;
import de.tum.in.pet.implementation.meanPayoff.MeanPayoffChecker;
import de.tum.in.pet.implementation.meanPayoff.RestrictedValueIteratorChecker;
import de.tum.in.pet.implementation.reachability.ReachChecker;
import de.tum.in.pet.util.CTMDPModelInfo;
import de.tum.in.pet.util.MinProbabilityCalculator;
import de.tum.in.probmodels.util.PrismHelper;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import prism.PrismException;

@SuppressWarnings("PMD.SystemPrintln")
public final class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  private Main() {
    // Empty
  }

  public static PrismHelper.PrismParseResult parse(CommandLine commandLine, Option modelOption,
      @Nullable Option propertiesOption, Option constantsOption)
      throws IOException, PrismException {
    String modelPath = commandLine.getOptionValue(modelOption.getLongOpt());
    @Nullable
    String propertiesPath = propertiesOption == null
        ? null : commandLine.getOptionValue(propertiesOption.getLongOpt());
    @Nullable
    String constantsString = commandLine.hasOption(constantsOption.getLongOpt())
        ? commandLine.getOptionValue(constantsOption.getLongOpt())
        : null;
    return PrismHelper.parse(modelPath, propertiesPath, constantsString);
  }

  public static void main(String... args) throws IOException, PrismException {
    if (args.length < 1) {
      System.out.println("I need a sub-tool (reachability, core)");
      System.exit(1);
    }

    logger.log(Level.INFO, "Invocation:\n{0}", String.join(" ", args));

    // TODO Ugly :-)
    String[] subArgs = new String[args.length - 1];
    System.arraycopy(args, 1, subArgs, 0, subArgs.length);

    NatBitSets.setFactory(new RoaringNatBitSetFactory());

    switch (args[0]) {
      case "reachability":
        ReachChecker.main(subArgs);
        break;
      case "core":
        CoreChecker.main(subArgs);
        break;
      case "uniform":
        CoreChecker.computeUniformisationRate(subArgs);
        break;
      case "mecVI":
        RestrictedValueIteratorChecker.main(subArgs);
        break;
      case "meanPayoff":
        MeanPayoffChecker.main(subArgs);
        break;
      case "pMin":
        MinProbabilityCalculator.main(subArgs);
        break;
      case "ctmdpModelInfo":
        CTMDPModelInfo.main(subArgs);
        break;
      case "pet-convert":
        PETConverter.main(subArgs);
        break;
      default:
        System.out.println("Unknown tool " + args[0]);
        System.exit(1);
        break;
    }
  }
}
