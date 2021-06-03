package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.RoaringNatBitSetFactory;
import de.tum.in.pet.Main;
import de.tum.in.pet.implementation.reachability.UnboundedReachValues;
import de.tum.in.pet.implementation.reachability.ValueUpdate;
import de.tum.in.pet.sampler.Iterator;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.util.CliHelper;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.DefaultExplorer;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.Generator;
import de.tum.in.probmodels.generator.MdpGenerator;
import de.tum.in.probmodels.generator.PrismRewardGenerator;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.util.PrismHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.State;
import parser.ast.ModulesFile;
import prism.*;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;
import java.util.function.IntPredicate;
import java.util.logging.Logger;

public final class MeanPayoffChecker {
  private static final Logger logger = Logger.getLogger(MeanPayoffChecker.class.getName());
  public static final int DEFAULT_THRESHOLD = 2;
  public static final double REWARD_UPPERBOUND = 1000;

  private MeanPayoffChecker(){

  }

  public static double solve(ModelGenerator generator, SuccessorHeuristic heuristic, double precision, int revisitThreshold) throws PrismException {
    ModelType modelType = generator.getModelType();
    switch (modelType) {
      case MDP:
        return solveMdp(generator, heuristic, precision, revisitThreshold);
      case CTMC:
      case DTMC:
      case LTS:
      case CTMDP:
      case PTA:
      case STPG:
      case SMG:
      default:
        throw new UnsupportedOperationException();
    }
  }

  private static <M extends Model> double solve(Explorer<State, M> explorer, RewardGenerator<State> rewardGenerator,
      SuccessorHeuristic heuristic, double precision, int revisitThreshold) throws PrismException {

    IntPredicate target = (x) -> x==-1;
    UnboundedValues values = new UnboundedReachValues(ValueUpdate.MAX_VALUE, target, 2*precision/REWARD_UPPERBOUND, heuristic);

    Iterator<State, M> valueIterator = new OnDemandValueIterator<>(explorer, values, rewardGenerator, revisitThreshold, REWARD_UPPERBOUND);
    valueIterator.run();

    int initState = explorer.initialStates().iterator().nextInt();
    Bounds bounds = valueIterator.bounds(initState);

    return REWARD_UPPERBOUND*bounds.average();

  }

  private static double solveMdp(ModelGenerator prismGenerator, SuccessorHeuristic heuristic, double precision, int revisitThreshold) throws PrismException {

    MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
    Generator<State> generator = new MdpGenerator(prismGenerator);
    var explorer = DefaultExplorer.of(partialModel, generator, false);

    RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(0, prismGenerator);

    return solve(explorer, rewardGenerator, heuristic, precision, revisitThreshold);

  }

  public static void main(String[] args) throws PrismException, IOException {
    Option precisionOption = new Option(null, "precision", true, "Precision");
    Option heuristicOption = CliHelper.getDefaultHeuristicOption();
    Option modelOption = new Option("m", "model", true, "Path to model file");
//    Option propertiesOption = new Option("p", "properties", true, "Path to properties file");
//    Option propertyNameOption = new Option(null, "property", true, "Name of property to check");
    Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
    Option revisitThresholdOption = new Option(null, "revisitThreshold", true, "Revisit Threshold");
//    Option expectedValuesOption = new Option(null, "expected", true,
//            "Comma separated list of the true values of the properties");
//    Option onlyPrintResultOption = new Option(null, "only-result", false,
//            "Only print result");
//    Option relativeErrorOption = new Option(null, "relative-error", false,
//            "Use relative error estimate");

    modelOption.setRequired(true);

    Options options = new Options()
            .addOption(precisionOption)
            .addOption(modelOption)
            .addOption(heuristicOption)
            .addOption(constantsOption)
            .addOption(revisitThresholdOption);

    CommandLine commandLine = CliHelper.parse(options, args);

    double precision = commandLine.hasOption(precisionOption.getLongOpt())
            ? Double.parseDouble(commandLine.getOptionValue(precisionOption.getLongOpt()))
            : Main.DEFAULT_PRECISION;

    int revisitThreshold = commandLine.hasOption(revisitThresholdOption.getLongOpt())
            ? Integer.parseInt(commandLine.getOptionValue(revisitThresholdOption.getLongOpt()))
            : DEFAULT_THRESHOLD;

    SuccessorHeuristic heuristic = CliHelper.parseHeuristic(
            commandLine.getOptionValue(heuristicOption.getLongOpt()), SuccessorHeuristic.WEIGHTED);

    NatBitSets.setFactory(new RoaringNatBitSetFactory());

    PrismHelper.PrismParseResult parse =
            Main.parse(commandLine, modelOption, null, constantsOption);
    ModulesFile modulesFile = parse.modulesFile();

    Prism prism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);

    double meanPayoff = solve(generator, heuristic, precision, revisitThreshold);

    System.out.println(meanPayoff);
  }

}
