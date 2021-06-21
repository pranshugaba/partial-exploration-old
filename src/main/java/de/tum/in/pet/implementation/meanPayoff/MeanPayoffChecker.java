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
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/* This code's purpose is to facilitate the testing of the OnDemandValueIterator. Right now, this code accepts 5
parameters, -m/--model (model file path) --precision, --const (defining constants in model , if any). Right now, only
MDPs are supported. This code should return correct results if the number of states is lesser than or equal to INT_MAX-3
the values INT_MAX, INT_MAX-1, INT_MAX-2 are assigned to special states.
* */
public final class MeanPayoffChecker {
  private static final Logger logger = Logger.getLogger(MeanPayoffChecker.class.getName());
  public static final int DEFAULT_THRESHOLD = 5;
  public static final double REWARD_UPPERBOUND = 10;

  private MeanPayoffChecker(){

  }

  public static double solve(ModelGenerator generator, int rewardIndex, SuccessorHeuristic heuristic, double precision, int revisitThreshold, double maxReward) throws PrismException {
    ModelType modelType = generator.getModelType();
    switch (modelType) {
      case MDP:
        return solveMdp(generator, rewardIndex, heuristic, precision, revisitThreshold, maxReward);
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

  private static <M extends Model> double solve(M partialModel, Generator<State> generator, RewardGenerator<State> rewardGenerator,
      SuccessorHeuristic heuristic, double precision, int revisitThreshold, double maxReward) throws PrismException {

    var explorer = DefaultExplorer.of(partialModel, generator, false);

    IntPredicate target = (x) -> x==Integer.MAX_VALUE;
    UnboundedValues values = new UnboundedReachValues(ValueUpdate.MAX_VALUE, target, 2*precision/maxReward, heuristic);

    Iterator<State, M> valueIterator = new OnDemandValueIterator<>(explorer, values, rewardGenerator, revisitThreshold, maxReward);
    valueIterator.run();

    int initState = explorer.initialStates().iterator().nextInt();
    Bounds bounds = valueIterator.bounds(initState);

    return maxReward*bounds.average();

  }

  private static double solveMdp(ModelGenerator prismGenerator, int rewardIndex, SuccessorHeuristic heuristic, double precision, int revisitThreshold, double maxReward) throws PrismException {

    MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
    Generator<State> generator = new MdpGenerator(prismGenerator);

    RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(rewardIndex, prismGenerator);

    return solve(partialModel, generator, rewardGenerator, heuristic, precision, revisitThreshold, maxReward);

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
    Option rewardModuleOption = new Option(null, "rewardModule", true, "Name of the reward module");
    Option maxRewardOption = new Option(null, "maxReward", true, "Estimated max reward value for a single transition in the model");

    modelOption.setRequired(true);

    Options options = new Options()
            .addOption(precisionOption)
            .addOption(modelOption)
            .addOption(heuristicOption)
            .addOption(constantsOption)
            .addOption(revisitThresholdOption)
            .addOption(rewardModuleOption)
            .addOption(maxRewardOption);

    CommandLine commandLine = CliHelper.parse(options, args);

    double precision = commandLine.hasOption(precisionOption.getLongOpt())
            ? Double.parseDouble(commandLine.getOptionValue(precisionOption.getLongOpt()))
            : Main.DEFAULT_PRECISION;

    int revisitThreshold = commandLine.hasOption(revisitThresholdOption.getLongOpt())
            ? Integer.parseInt(commandLine.getOptionValue(revisitThresholdOption.getLongOpt()))
            : DEFAULT_THRESHOLD;

    double maxReward = commandLine.hasOption(maxRewardOption.getLongOpt())
            ? Double.parseDouble(commandLine.getOptionValue(maxRewardOption.getLongOpt()))
            : REWARD_UPPERBOUND;

    SuccessorHeuristic heuristic = CliHelper.parseHeuristic(
            commandLine.getOptionValue(heuristicOption.getLongOpt()), SuccessorHeuristic.PROB);

    NatBitSets.setFactory(new RoaringNatBitSetFactory());

    PrismHelper.PrismParseResult parse =
            Main.parse(commandLine, modelOption, null, constantsOption);
    ModulesFile modulesFile = parse.modulesFile();

    Prism prism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);

    int rewardIndex = commandLine.hasOption(rewardModuleOption.getLongOpt())
            ? generator.getRewardStructIndex(commandLine.getOptionValue(rewardModuleOption.getLongOpt()))
            : 0;

    if(rewardIndex==-1){
      throw new NoSuchElementException("Reward module "+commandLine.getOptionValue(rewardModuleOption.getLongOpt())+" not found");
    }

    double meanPayoff = solve(generator, rewardIndex, heuristic, precision, revisitThreshold, maxReward);

    logger.log(Level.INFO, "Result is {0}", new Object[] {meanPayoff});
  }

}
