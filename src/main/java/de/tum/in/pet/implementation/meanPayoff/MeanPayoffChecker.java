package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.RoaringNatBitSetFactory;
import de.tum.in.pet.Main;
import de.tum.in.pet.implementation.reachability.*;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.util.CliHelper;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorers;
import de.tum.in.probmodels.explorer.InformationLevel;
import de.tum.in.probmodels.generator.*;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.util.PrismHelper;
import explicit.CTMDP;
import it.unimi.dsi.fastutil.doubles.Double2LongFunction;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.State;
import parser.ast.ModulesFile;
import parser.ast.Update;
import prism.*;
import simulator.ModulesFileModelGenerator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  public static final double PMIN_LOWERBOUND = 1.0e-6;
  public static final double DEFAULT_ERROR_TOLERANCE = 0.1;
  public static final int DEFAULT_ITERATION_SAMPLE = 10000;
  public static final long DEFAULT_TIMEOUT = 1800000;

  private static final List<Pair<Long, Bounds>> timeVBound = new ArrayList<>();

  private static final List<String> additionalWriteInfo = new ArrayList<>();

  public static void writeResults(CommandLine commandLine) throws IOException {
    String filename = "temp.txt";
    BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

    StringBuilder modelDetails = new StringBuilder();

    Option[] options = commandLine.getOptions();

    for (Option option : options) {
      String shortOption = option.getOpt();
      String longOption = option.getLongOpt();
      if (shortOption!=null && commandLine.hasOption(shortOption)) {
        modelDetails.append("-").append(shortOption).append(" ");
        if (option.hasArg()) {
          modelDetails.append(commandLine.getOptionValue(shortOption)).append(" ");
        }
      }
      else if (longOption!=null && commandLine.hasOption(longOption)) {
        modelDetails.append("--").append(longOption).append(" ");
        if (option.hasArg()) {
          modelDetails.append(commandLine.getOptionValue(longOption)).append(" ");
        }
      }
    }

    writer.write(modelDetails.toString());
    writer.newLine();

    StringBuilder times = new StringBuilder();
    StringBuilder lowerBounds = new StringBuilder();
    StringBuilder upperBounds = new StringBuilder();

    for (Pair<Long, Bounds> timeBounds: timeVBound){
      times.append(timeBounds.first).append(" ");
      lowerBounds.append(timeBounds.second.lowerBound()).append(" ");
      upperBounds.append(timeBounds.second.upperBound()).append(" ");
    }

    writer.write(times.toString());
    writer.newLine();
    writer.write(lowerBounds.toString());
    writer.newLine();
    writer.write(upperBounds.toString());
    writer.newLine();

    for (String info : additionalWriteInfo) {
      writer.write(info);
      writer.newLine();
    }

    writer.close();
  }

  public static double solve(ModelGenerator generator, InformationLevel informationLevel, int rewardIndex,
                             SuccessorHeuristic heuristic, UpdateMethod updateMethod, double precision,
                             int revisitThreshold, double maxReward, double pMin, double errorTolerance, int iterSamples,
                             long timeout, boolean getErrorProbability)
          throws PrismException {
    ModelType modelType = generator.getModelType();
    switch (modelType) {
      case MDP:
        return solveMdp(generator, informationLevel, rewardIndex, heuristic, updateMethod, precision, revisitThreshold,
                maxReward, pMin, errorTolerance, iterSamples, timeout, getErrorProbability);
      case CTMC:
      case DTMC:
      case LTS:
      case CTMDP:
        return solveCtmdp(generator, informationLevel, rewardIndex, heuristic, updateMethod, precision, revisitThreshold,
            maxReward, pMin, errorTolerance, iterSamples, timeout);
      case PTA:
      case STPG:
      case SMG:
      default:
        throw new UnsupportedOperationException();
    }
  }

  private static <S, M extends Model> double solve(M partialModel, Generator<S> generator, InformationLevel informationLevel,
      RewardGenerator<S> rewardGenerator, SuccessorHeuristic heuristic, UpdateMethod updateMethod, double precision,
      int revisitThreshold, double maxReward, double pMin, double errorTolerance, int iterSamples, long timeout, boolean getErrorProbability)
          throws PrismException {

    var explorer = Explorers.getExplorer(partialModel, generator, informationLevel, false);

    IntPredicate target = (x) -> x==Integer.MAX_VALUE;
    OnDemandValueIterator<S, M> valueIterator;

    if (informationLevel==InformationLevel.WHITEBOX) {
      UnboundedValues values = new UnboundedReachValues(ValueUpdate.MAX_VALUE, target, precision / maxReward, heuristic);

      valueIterator = new OnDemandValueIterator<>(explorer, values, rewardGenerator, revisitThreshold, maxReward, precision / maxReward, System.currentTimeMillis()+timeout);
    }
    else if (informationLevel==InformationLevel.BLACKBOX) {
      Double2LongFunction nSampleFunction = s -> iterSamples;

      UnboundedValues values = new BlackUnboundedReachValues(ValueUpdate.MAX_VALUE, updateMethod, target, precision / maxReward, heuristic);

      valueIterator = new BlackOnDemandValueIterator<>(explorer, values, rewardGenerator,
              revisitThreshold, maxReward, pMin, errorTolerance, nSampleFunction, precision / maxReward, System.currentTimeMillis()+timeout, getErrorProbability);
    }
    else{
      Double2LongFunction nSampleFunction = s -> iterSamples;

      UnboundedValues values = new GreyUnboundedReachValues(ValueUpdate.MAX_VALUE, updateMethod, target, precision / maxReward, heuristic);

      valueIterator = new GreyOnDemandValueIterator<>(explorer, values, rewardGenerator,
              revisitThreshold, maxReward, pMin, errorTolerance, nSampleFunction, precision / maxReward, System.currentTimeMillis()+timeout);
    }

    valueIterator.run();

    int initState = explorer.initialStates().iterator().nextInt();
    Bounds bounds = valueIterator.bounds(initState);

    logger.log(Level.INFO, "Explored states {0}", new Object[] {explorer.exploredStateCount()});

    timeVBound.addAll(valueIterator.timeVBound);
    additionalWriteInfo.addAll(valueIterator.additionalWriteInfo);

    return maxReward*bounds.average();

  }

  private static double solveCtmdp(ModelGenerator prismGenerator, InformationLevel informationLevel, int rewardIndex,
                                    SuccessorHeuristic heuristic, UpdateMethod updateMethod, double precision,
                                    int revisitThreshold, double maxReward, double pMin, double errorTolerance,
                                    int iterSamples, long timeout)
          throws PrismException {

    MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
    Generator<State> generator = new CtmdpGenerator(prismGenerator);

    RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(rewardIndex, prismGenerator);

    return solve(partialModel, generator, informationLevel, rewardGenerator, heuristic, updateMethod, precision, revisitThreshold, maxReward, pMin, errorTolerance, iterSamples, timeout);

  }

  private static double solveMdp(ModelGenerator prismGenerator, InformationLevel informationLevel, int rewardIndex,
                                 SuccessorHeuristic heuristic, UpdateMethod updateMethod, double precision,
                                 int revisitThreshold, double maxReward, double pMin, double errorTolerance,
                                 int iterSamples, long timeout, boolean getErrorProbability)
          throws PrismException {

    MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
    Generator<State> generator = new MdpGenerator(prismGenerator);

    RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(rewardIndex, prismGenerator);

    return solve(partialModel, generator, informationLevel, rewardGenerator, heuristic, updateMethod, precision, revisitThreshold, maxReward, pMin, errorTolerance, iterSamples, timeout, getErrorProbability);

  }

  public static void main(String[] args) throws PrismException, IOException {
    Option precisionOption = new Option(null, "precision", true, "Required precision of the returned value. (Default: 10^{-6})");
    Option heuristicOption = CliHelper.getDefaultHeuristicOption();
    Option modelOption = new Option("m", "model", true, "Path to model file");
//    Option propertiesOption = new Option("p", "properties", true, "Path to properties file");
//    Option propertyNameOption = new Option(null, "property", true, "Name of property to check");
    Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
    Option revisitThresholdOption = new Option(null, "revisitThreshold", true, "Number of times a state should be visited before a sampling run is stopped. (Default: 5)");
//    Option expectedValuesOption = new Option(null, "expected", true,
//            "Comma separated list of the true values of the properties");
//    Option onlyPrintResultOption = new Option(null, "only-result", false,
//            "Only print result");
//    Option relativeErrorOption = new Option(null, "relative-error", false,
//            "Use relative error estimate");
    Option rewardModuleOption = new Option(null, "rewardModule", true, "Name of the reward module in the model file.");
    Option maxRewardOption = new Option(null, "maxReward", true, "Estimated max reward value for a single transition in the model. (Default: 10)");
    Option informationOption = CliHelper.getDefaultInformationLevelOption();
    Option pMinOption = new Option(null, "pMin", true, "Estimated minimum probability of a transition in the model. (Only used for BlackBox models)");
    Option errorToleranceOption = new Option(null, "errorTolerance", true, "Error tolerance for blackbox exploration.");
    Option iterationSamplesOption =  new Option(null, "iterSamples", true, "Number of sample paths to be generated per episodic run.");
    Option updateMethodOption = new Option(null, "updateMethod", true, "Update method to be used (greybox/blackbox)");
    Option timeoutOption = new Option(null, "timeout", true, "Time before experiment forcefully terminates");
    Option getErrorProbabilityOption = new Option(null, "getErrorProbability", false, "Computes the error probability for blackbox with greybox equations");

    modelOption.setRequired(true);

    Options options = new Options()
            .addOption(modelOption)
            .addOption(constantsOption)
            .addOption(precisionOption)
            .addOption(heuristicOption)
            .addOption(revisitThresholdOption)
            .addOption(rewardModuleOption)
            .addOption(maxRewardOption)
            .addOption(informationOption)
            .addOption(pMinOption)
            .addOption(errorToleranceOption)
            .addOption(iterationSamplesOption)
            .addOption(updateMethodOption)
            .addOption(timeoutOption)
            .addOption(getErrorProbabilityOption);

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

    double pMin = commandLine.hasOption(pMinOption.getLongOpt())
            ? Double.parseDouble(commandLine.getOptionValue(pMinOption.getLongOpt()))
            : PMIN_LOWERBOUND;

    double errorTolerance = commandLine.hasOption(errorToleranceOption.getLongOpt())
            ? Double.parseDouble(commandLine.getOptionValue(errorToleranceOption.getLongOpt()))
            : DEFAULT_ERROR_TOLERANCE;

    int iterSamples = commandLine.hasOption(iterationSamplesOption.getLongOpt())
            ? Integer.parseInt(commandLine.getOptionValue(iterationSamplesOption.getLongOpt()))
            : DEFAULT_ITERATION_SAMPLE;

    long timeout = commandLine.hasOption(timeoutOption.getLongOpt())
            ? Long.parseLong(commandLine.getOptionValue(timeoutOption.getLongOpt()))
            : DEFAULT_TIMEOUT;

    boolean getErrorProbability = commandLine.hasOption(getErrorProbabilityOption.getLongOpt());

    SuccessorHeuristic heuristic = CliHelper.parseHeuristic(
            commandLine.getOptionValue(heuristicOption.getLongOpt()), SuccessorHeuristic.PROB);

    InformationLevel informationLevel = CliHelper.parseInformationLevel(
            commandLine.getOptionValue(informationOption.getLongOpt()), InformationLevel.WHITEBOX);

    UpdateMethod updateMethod = CliHelper.parseUpdateMethod(
            commandLine.getOptionValue(updateMethodOption.getLongOpt()), UpdateMethod.GREYBOX);

    NatBitSets.setFactory(new RoaringNatBitSetFactory());

    double startTime1 = System.currentTimeMillis();
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

    long startTime2 = System.currentTimeMillis();
    timeVBound.add(new Pair<>(startTime2, Bounds.of(0, maxReward)));
    double meanPayoff = solve(generator, informationLevel, rewardIndex, heuristic, updateMethod, precision, revisitThreshold, maxReward, pMin, errorTolerance, iterSamples, timeout, getErrorProbability);
    long endTime = System.currentTimeMillis();

    writeResults(commandLine);

    logger.log(Level.INFO, "Time to parse, construct model, and compute {0}", new Object[] {endTime-startTime1});
    logger.log(Level.INFO, "Time to compute {0}", new Object[] {endTime-startTime2});
    logger.log(Level.INFO, "Result is {0}", new Object[] {meanPayoff});
  }

}
