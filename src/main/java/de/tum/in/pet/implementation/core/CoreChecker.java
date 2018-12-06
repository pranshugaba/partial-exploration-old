package de.tum.in.pet.implementation.core;

import static de.tum.in.pet.util.PrismHelper.PrismParseResult;
import static de.tum.in.pet.util.PrismHelper.parse;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.tum.in.naturals.set.DefaultNatBitSetFactory;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.explorer.DefaultExplorer;
import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.generator.CtmcEmbeddingGenerator;
import de.tum.in.pet.generator.CtmcUniformizingGenerator;
import de.tum.in.pet.generator.DtmcGenerator;
import de.tum.in.pet.generator.Generator;
import de.tum.in.pet.generator.MdpGenerator;
import de.tum.in.pet.graph.MecComponentAnalyser;
import de.tum.in.pet.graph.SccComponentAnalyser;
import de.tum.in.pet.model.DTMC;
import de.tum.in.pet.model.MDP;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedSampler;
import de.tum.in.pet.util.Util;
import de.tum.in.pet.values.StateValuesBounded;
import explicit.CTMCModelChecker;
import explicit.ConstructModel;
import explicit.DTMCModelChecker;
import explicit.ECComputer;
import explicit.MDPModelChecker;
import explicit.Model;
import explicit.ModelCheckerResult;
import explicit.NondetModel;
import explicit.ProbModelChecker;
import explicit.SCCComputer;
import explicit.SCCConsumerStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import parser.State;
import parser.ast.Expression;
import parser.ast.ModulesFile;
import prism.ModelGenerator;
import prism.ModelType;
import prism.Prism;
import prism.PrismDevNullLog;
import prism.PrismException;
import prism.PrismSettings;
import simulator.ModulesFileModelGenerator;

@SuppressWarnings("PMD")
public final class CoreChecker {
  private static final Logger logger = Logger.getLogger(CoreChecker.class.getName());

  private CoreChecker() {}

  public static void main(String... args) throws IOException, PrismException {
    Option precisionOption = new Option(null, "precision", true, "Precision");
    Option completeOption = new Option(null, "complete", false, "Build complete model");
    Option completeAnalysisOption = new Option(null, "complete-analysis", false,
        "Compute number and size of MECs / SCCs in the complete model");
    Option unboundedOption = new Option(null, "unbounded", false, "Build unbounded model");
    Option boundedOption = new Option(null, "bounded", true,
        "Build bounded model with given step bound");
    // Option iterative = new Option(null, "iterative", true, "Build complete model");
    Option validateOption = new Option(null, "validate", false, "Validate the core property");
    Option stabilityOption = new Option(null, "stability", false, "Compute stability");
    Option stabilityStepsOption =
        new Option(null, "stability-steps", true, "Step bounds for stability");
    Option heuristicOption = new Option(null, "heuristic", true, "Heuristic to use");
    Option modelFileOption = new Option("m", "model", true, "Path to model file");
    Option propertiesFileOption = new Option("p", "properties", true, "Path to properties file");
    Option constantsOption = new Option("c", "const", true, "Constants of model/property file, "
        + "comma separated list");
    Option uniformizationOption = new Option(null, "uniformization", true,
        "Uniformization rate for CTMC");
    Option boundedUpdateOption = new Option(null, "bounded-update", true, "The type of bounded "
        + "update to use (\"dense\", \"simple,n\")");

    modelFileOption.setRequired(true);

    Options options = new Options()
        .addOption(precisionOption)
        .addOption(completeOption)
        .addOption(completeAnalysisOption)
        .addOption(unboundedOption)
        .addOption(boundedOption)
        .addOption(validateOption)
        .addOption(stabilityOption)
        .addOption(stabilityStepsOption)
        .addOption(heuristicOption)
        .addOption(modelFileOption)
        .addOption(propertiesFileOption)
        .addOption(constantsOption)
        .addOption(uniformizationOption)
        .addOption(boundedUpdateOption);


    HelpFormatter formatter = new HelpFormatter();

    CommandLineParser cliParser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = cliParser.parse(options, args);
    } catch (ParseException e) {
      System.out.println("Failed to parse command line arguments: " + e.getMessage());
      formatter.printHelp("argument list", options);
      System.exit(1);
      throw new AssertionError();
    }


    final boolean complete = commandLine.hasOption(completeOption.getLongOpt());
    final boolean completeAnalysis = commandLine.hasOption(completeAnalysisOption.getLongOpt());
    final boolean unbounded = commandLine.hasOption(unboundedOption.getLongOpt());
    final boolean validateCoreProperty = commandLine.hasOption(validateOption.getLongOpt());
    final boolean boundedExtrapolation = false;
    final boolean boundedStabilityAnalysis = commandLine.hasOption(stabilityOption.getLongOpt());
    final double precision = commandLine.hasOption(precisionOption.getLongOpt())
        ? Double.parseDouble(commandLine.getOptionValue(precisionOption.getLongOpt()))
        : 1e-6;
    final Double ctmcUniformRate = commandLine.hasOption(uniformizationOption.getLongOpt())
        ? Double.parseDouble(commandLine.getOptionValue(uniformizationOption.getLongOpt()))
        : null;

    SuccessorHeuristic heuristic;
    if (commandLine.hasOption(heuristicOption.getLongOpt())) {
      String heuristicSetting = commandLine.getOptionValue(heuristicOption.getLongOpt());
      try {
        heuristic = SuccessorHeuristic.valueOf(heuristicSetting);
      } catch (IllegalArgumentException e) {
        String values = Arrays.stream(SuccessorHeuristic.values())
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        System.out.println("Unknown heuristic " + heuristicSetting + ". Possible values are: "
            + values);
        System.exit(1);
        throw new AssertionError();
      }
    } else {
      heuristic = SuccessorHeuristic.WEIGHTED;
    }

    int stepBound;
    if (commandLine.hasOption(boundedOption.getLongOpt())) {
      stepBound = Integer.parseInt(commandLine.getOptionValue(boundedOption.getLongOpt()));
      if (stepBound <= 0) {
        System.out.println("Step bound must be larger than 0");
        System.exit(1);
      }
    } else {
      stepBound = -1;
    }

    boolean bounded = stepBound > 0;

    List<Integer> escapeAnalysis;

    if (commandLine.hasOption(stabilityStepsOption.getLongOpt())) {
      escapeAnalysis = Arrays.stream(commandLine.getOptionValues(stabilityStepsOption.getLongOpt()))
          .map(s -> s.split(","))
          .flatMap(Arrays::stream)
          .map(Integer::parseInt)
          .collect(Collectors.toList());
    } else {
      escapeAnalysis = Arrays.asList(0, 1, 5, 10, 20, 50, 100);
    }

    Supplier<StateValuesBounded> boundedUpdateSupplier;

    if (commandLine.hasOption(boundedUpdateOption.getLongOpt())) {
      String[] option = commandLine.getOptionValue(boundedUpdateOption.getLongOpt()).split(",");

      if (option[0].equals("dense")) {
        boundedUpdateSupplier = StateValuesBoundedCoreDense::new;
      } else if (option[0].equals("simple")) {
        int count = Integer.parseInt(option[1]);
        boundedUpdateSupplier = () -> new StateValuesBoundedCoreApproximationSimple(count);
      } else {
        System.out.println("Invalid state update");
        System.exit(1);
        throw new AssertionError();
      }
    } else {
      boundedUpdateSupplier = () -> new StateValuesBoundedCoreApproximationSimple(5);
    }


    NatBitSets.setFactory(new DefaultNatBitSetFactory((a, b) -> true));
    // NatBitSets.setFactory(new RoaringNatBitSetFactory());

    String modelPath = commandLine.getOptionValue(modelFileOption.getLongOpt());
    @Nullable
    String constantsString = commandLine.hasOption(constantsOption.getLongOpt())
        ? commandLine.getOptionValue(constantsOption.getLongOpt())
        : null;

    PrismParseResult parse = parse(modelPath, null, constantsString);
    ModulesFile modulesFile = parse.modulesFile();


    Prism prism = new Prism(new PrismDevNullLog());
    Prism mcPrism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);
    ModelType modelType = modulesFile.getModelType();

    mcPrism.getSettings().set(PrismSettings.PRISM_TERM_CRIT_PARAM, precision);

    List<AnnotatedResult> construction = new ArrayList<>();
    Multimap<Expression, AnnotatedResult> results = HashMultimap.create();
    SortedMap<Integer, AnnotatedResult> boundedEscapeAnalysisResults = new TreeMap<>();

    logger.log(Level.INFO, String.format("Running Core construction on model %s "
        + "with heuristic %s and precision: %3.2g%n", modelPath, heuristic, precision));

    Model completeModel;

    if (complete) {
      logger.log(Level.INFO, "Building complete model");
      Timer timer = new Timer("complete");
      ConstructModel constructModel = new ConstructModel(mcPrism);
      completeModel = constructModel.constructModel(generator);
      construction.add(timer.finish(completeModel.getNumStates()));

      if (completeAnalysis) {
        if (completeModel instanceof NondetModel) {
          Timer mecTimer = new Timer("complete MECs");
          ECComputer ecComputer = ECComputer.createECComputer(mcPrism, (NondetModel) completeModel);
          List<BitSet> mecs = ecComputer.getMECStates();
          double avg = mecs.stream().mapToInt(BitSet::cardinality).average().orElse(-1.0);
          int max = mecs.stream().mapToInt(BitSet::cardinality).max().orElse(-1);
          int count = mecs.size();
          construction.add(mecTimer.finish(String.format("Count: %d, max: %d, avg: %.1f",
              count, max, avg)));
        } else {
          SCCConsumerStore store = new SCCConsumerStore();
          Timer sccTimer = new Timer("complete SCCs");
          SCCComputer computer = SCCComputer.createSCCComputer(mcPrism, completeModel, store);
          computer.computeSCCs(false);
          List<BitSet> sccs = store.getSCCs();
          double avg = sccs.stream().mapToInt(BitSet::cardinality).average().orElse(-1.0);
          int max = sccs.stream().mapToInt(BitSet::cardinality).max().orElse(-1);
          int count = sccs.size();
          construction.add(sccTimer.finish(String.format("Count: %d, max: %d, avg: %.1f",
              count, max, avg)));
        }
      }
    }

    if (modelType == ModelType.MDP) {
      MDPModelChecker mc = new MDPModelChecker(mcPrism);
      mc.setModulesFileAndPropertiesFile(modulesFile, null, generator);

      if (unbounded) {
        logger.log(Level.INFO, "Building unbounded MDP core");

        Timer timer = new Timer("unbounded");
        Explorer<State, MDP> explorer =
            new DefaultExplorer<>(new MDP(), new MdpGenerator(generator));
        StateValuesUnboundedCore stateValues = new StateValuesUnboundedCore();
        StateUpdateCore<State> stateUpdate = new StateUpdateCore<>(precision);
        UnboundedSampler<State, MDP> unboundedBuilder = new UnboundedSampler<>(explorer,
            stateValues,
            heuristic, stateUpdate, stateUpdate, stateUpdate, new MecComponentAnalyser());
        unboundedBuilder.build();
        AnnotatedModel<? extends MDP> unboundedPartial = unboundedBuilder.getModel();
        construction.add(timer.finish(unboundedPartial.exploredStates.size()));

        // unboundedPartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, mc, unboundedPartial, -1);
        }
      }

      if (bounded) {
        logger.log(Level.INFO, "Building {0}-bounded sampling MDP core", stepBound);

        Timer timer = new Timer("bounded");
        Explorer<State, MDP> mdpExplorer =
            new DefaultExplorer<>(new MDP(), new MdpGenerator(generator));
        CoreBoundedSamplingBuilder<State, MDP> boundedBuilder =
            new CoreBoundedSamplingBuilder<>(prism, mdpExplorer, stepBound, precision, heuristic,
                boundedUpdateSupplier.get());
        AnnotatedModel<? extends MDP> boundedPartial = boundedBuilder.build();
        construction.add(timer.finish(boundedPartial.model.getNumStates()));

        // boundedPartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, mc, boundedPartial, stepBound);
        }

        /* if (boundedExtrapolation) {
          for (int approximationSteps : extrapolationApproximation) {
            Timer approximationTimer = new Timer(String.valueOf(approximationSteps));
            ModelCheckerResult result =
                computeFringeReachability(mc, boundedPartial, approximationSteps);
            double max = boundedPartial.exploredStates.intStream()
                .mapToDouble(i -> result.soln[i])
                .max().orElse(Double.NaN);
            boundedExtrapolationResults.put(approximationSteps, approximationTimer.finish(max));
          }
        } */
        if (boundedStabilityAnalysis) {
          int initialState = boundedPartial.model.getInitialStates().iterator().nextInt();
          for (int escapeAnalysisSteps : escapeAnalysis) {
            Timer approximationTimer = new Timer(String.valueOf(escapeAnalysisSteps));
            ModelCheckerResult result =
                computeFringeReachability(mc, boundedPartial, stepBound + escapeAnalysisSteps);
            boundedEscapeAnalysisResults.put(escapeAnalysisSteps,
                approximationTimer.finish(result.soln[initialState]));
          }
        }
      }
    } else if (modelType == ModelType.DTMC) {
      DTMCModelChecker mc = new DTMCModelChecker(mcPrism);

      if (unbounded) {
        logger.log(Level.INFO, "Building unbounded DTMC core");

        Timer timer = new Timer("unbounded");
        Explorer<State, DTMC> explorer =
            new DefaultExplorer<>(new DTMC(), new DtmcGenerator(generator));
        StateValuesUnboundedCore stateValues = new StateValuesUnboundedCore();
        StateUpdateCore<State> stateUpdate = new StateUpdateCore<>(precision);
        UnboundedSampler<State, DTMC> unboundedSamplingBuilder = new UnboundedSampler<>(explorer,
            stateValues, heuristic, stateUpdate, stateUpdate, stateUpdate,
            new SccComponentAnalyser());
        unboundedSamplingBuilder.build();
        AnnotatedModel<? extends DTMC> unboundedPartial = unboundedSamplingBuilder.getModel();
        construction.add(timer.finish(unboundedPartial.exploredStates.size()));

        // unboundedPartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, mc, unboundedPartial, -1);
        }
      }

      if (bounded) {
        /*
        logger.log(Level.INFO, "Building bounded iterative DTMC core");

        Timer iterativeTimer = new Timer("bounded iterative");
        BoundedDTMCCoreIterativeBuilder boundedIterativeBuilder =
            new BoundedDTMCCoreIterativeBuilder(generator, remainingSteps, precision);
        AnnotatedModel<DTMC> boundedIterativePartial = boundedIterativeBuilder.build();
        construction.add(iterativeTimer.finish(boundedIterativePartial.exploredStates.size()));

        boundedIterativePartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, mc, boundedIterativePartial, remainingSteps);
        }

        results.putAll(checkExpressions("bounded iterative", expressions, mc,
            boundedIterativePartial.model));
        */

        logger.log(Level.INFO, "Building {0}-bounded sampling DTMC core", stepBound);

        Timer samplingTimer = new Timer("bounded sampling");
        Explorer<State, DTMC> explorer =
            new DefaultExplorer<>(new DTMC(), new DtmcGenerator(generator));
        CoreBoundedSamplingBuilder<State, DTMC> boundedSamplingBuilder =
            new CoreBoundedSamplingBuilder<>(prism, explorer, stepBound, precision, heuristic,
                boundedUpdateSupplier.get());
        AnnotatedModel<? extends DTMC> boundedSamplingPartial = boundedSamplingBuilder.build();
        construction.add(samplingTimer.finish(boundedSamplingPartial.exploredStates.size()));

        // boundedSamplingPartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, mc, boundedSamplingPartial, stepBound);
        }

        /* if (boundedExtrapolation) {
          for (int approximationSteps : extrapolationApproximation) {
            Timer approximationTimer = new Timer(String.valueOf(approximationSteps));
            ModelCheckerResult result =
                computeFringeReachability(mc, boundedSamplingPartial, approximationSteps);
            double max = boundedSamplingPartial.exploredStates.intStream()
                .mapToDouble(i -> result.soln[i])
                .max().orElse(Double.NaN);
            boundedExtrapolationResults.put(approximationSteps, approximationTimer.finish(max));
          }
        } */
        if (boundedStabilityAnalysis) {
          int initialState = boundedSamplingPartial.model.getInitialStates().iterator().nextInt();
          for (int escapeAnalysisSteps : escapeAnalysis) {
            Timer approximationTimer = new Timer(String.valueOf(escapeAnalysisSteps));
            ModelCheckerResult result = computeFringeReachability(mc, boundedSamplingPartial,
                stepBound + escapeAnalysisSteps);
            boundedEscapeAnalysisResults.put(escapeAnalysisSteps,
                approximationTimer.finish(result.soln[initialState]));
          }
        }
      }
    }
    if (modelType == ModelType.CTMC) {
      CTMCModelChecker mc = new CTMCModelChecker(mcPrism);
      mc.setModulesFileAndPropertiesFile(modulesFile, null, generator);

      if (unbounded) {
        logger.log(Level.INFO, "Building unbounded CTMC core");

        Timer timer = new Timer("unbounded");

        Generator<State> stateGenerator = ctmcUniformRate == null
            ? new CtmcEmbeddingGenerator(generator)
            : new CtmcUniformizingGenerator(generator, ctmcUniformRate);
        Explorer<State, DTMC> explorer = new DefaultExplorer<>(new DTMC(), stateGenerator);
        StateValuesUnboundedCore stateValues = new StateValuesUnboundedCore();
        StateUpdateCore<State> stateUpdate = new StateUpdateCore<>(precision);
        UnboundedSampler<State, DTMC> sampler = new UnboundedSampler<>(explorer, stateValues,
            heuristic, stateUpdate, stateUpdate, stateUpdate, new SccComponentAnalyser());
        sampler.build();
        AnnotatedModel<? extends DTMC> unboundedPartial = sampler.getModel();
        construction.add(timer.finish(unboundedPartial.exploredStates.size()));

        // unboundedPartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, mc, unboundedPartial, -1);
        }
      }

      if (bounded) {
        logger.log(Level.INFO, "Building {0}-bounded sampling CTMC core", stepBound);

        Timer samplingTimer = new Timer("bounded sampling");

        Generator<State> stateGenerator = ctmcUniformRate == null
            ? new CtmcEmbeddingGenerator(generator)
            : new CtmcUniformizingGenerator(generator, ctmcUniformRate);
        Explorer<State, DTMC> explorer = new DefaultExplorer<>(new DTMC(), stateGenerator);

        CoreBoundedSamplingBuilder<State, ? extends DTMC> boundedSamplingBuilder =
            new CoreBoundedSamplingBuilder<>(prism, explorer, stepBound, precision, heuristic,
                boundedUpdateSupplier.get());

        AnnotatedModel<? extends DTMC> boundedSamplingPartial = boundedSamplingBuilder.build();
        construction.add(samplingTimer.finish(boundedSamplingPartial.exploredStates.size()));

        // boundedSamplingPartial.model.findDeadlocks(true);

        if (validateCoreProperty) {
          checkCoreProperty(precision, new DTMCModelChecker(mcPrism), boundedSamplingPartial,
              stepBound);
        }

        if (boundedStabilityAnalysis) {
          int initialState = boundedSamplingPartial.model.getInitialStates().iterator().nextInt();
          for (int escapeAnalysisSteps : escapeAnalysis) {
            Timer approximationTimer = new Timer(String.valueOf(escapeAnalysisSteps));
            ModelCheckerResult result = computeFringeReachability(new DTMCModelChecker(mcPrism),
                boundedSamplingPartial, stepBound + escapeAnalysisSteps);
            boundedEscapeAnalysisResults.put(escapeAnalysisSteps,
                approximationTimer.finish(result.soln[initialState]));
          }
        }
      }
    }

    StringBuilder resultString = new StringBuilder("Results:\n");
    construction.forEach(result -> resultString.append(" ").append(result.name).append(": ")
        .append(result.result).append(" (").append(result.timeInMillis()).append(" ms)")
        .append('\n'));
    resultString.append('\n');

    results.asMap().forEach((expression, annotatedResults) -> {
      resultString.append(expression).append("\n");
      List<AnnotatedResult> orderedResults = new ArrayList<>(annotatedResults);
      orderedResults.sort(Comparator.comparing(a -> a.name));
      orderedResults.forEach(result -> resultString.append("  ").append(result.name)
          .append(": ").append(result.result).append(" (").append(result.timeInMillis())
          .append(" ms)").append('\n'));
      resultString.append('\n');
    });
    /*
    if (boundedExtrapolation) {
      resultString.append("\nExtrapolation: \n");
      boundedExtrapolationResults.forEach((steps, result) -> {
        double escapeProbability = (Double) result.result;
        resultString.append("  ").append(steps).append(": ").append(escapeProbability)
            .append(" (").append(result.timeInMillis()).append(" ms)").append('\n');
      });
    } */
    if (boundedStabilityAnalysis) {
      resultString.append("\nStability Analysis: \n");
      boundedEscapeAnalysisResults.forEach((steps, result) -> {
        double escapeProbability = (Double) result.result;
        resultString.append("  ").append(steps).append(": ").append(escapeProbability)
            .append(" (").append(result.timeInMillis()).append(" ms)").append('\n');
      });
    }

    System.out.println(resultString);
  }

  private static ModelCheckerResult computeFringeReachability(ProbModelChecker mc,
      AnnotatedModel<?> partialModel, int stepBound) throws PrismException {
    BitSet target = NatBitSets.toBitSet(partialModel.getFringeStates());
    Model model = partialModel.model;

    if (model instanceof DTMC) {
      DTMCModelChecker dtmcChecker = (DTMCModelChecker) mc;

      return stepBound < 0
          ? dtmcChecker.computeReachProbs((DTMC) model, target)
          : dtmcChecker.computeBoundedReachProbs((DTMC) model, target, stepBound);
    }
    if (model instanceof MDP) {
      MDPModelChecker mdpChecker = (MDPModelChecker) mc;

      return stepBound < 0
          ? mdpChecker.computeReachProbs((MDP) model, target, false)
          : mdpChecker.computeBoundedReachProbs((MDP) model, target, stepBound, false);
    }
    throw new IllegalArgumentException();
  }

  private static void checkCoreProperty(double precision, ProbModelChecker mc,
      AnnotatedModel<?> partialModel, int stepBound) throws PrismException {
    logger.log(Level.INFO, "Checking core property");
    int initialState = partialModel.model.getFirstInitialState();
    ModelCheckerResult result = computeFringeReachability(mc, partialModel, stepBound);
    double reachability = result.soln[initialState];
    logger.log(Level.INFO, () -> String.format("Reachability: %f", reachability));
    if (!Util.doublesAreLessOrEqual(reachability, precision)) {
      throw new PrismException("Core property violated!");
    }
  }

  private static final class AnnotatedResult {
    public final String name;
    @SuppressWarnings("PublicField")
    public final Object result;
    public final long time;

    public AnnotatedResult(String name, Object result, long time) {
      this.name = name;
      this.result = result;
      this.time = time;
    }

    public double timeInMillis() {
      return time / (double) TimeUnit.MILLISECONDS.toNanos(1);
    }
  }

  private static final class Timer {
    private final long time;
    private final String name;

    public Timer(String name) {
      this.name = name;
      this.time = System.nanoTime();
    }

    public AnnotatedResult finish(Object result) {
      return new AnnotatedResult(name, result, System.nanoTime() - time);
    }
  }

  /*
  public void pruneStates(StateUpdate bounds) throws PrismException {
    MDP model = partialExplorer.getModel();
    MDPModelChecker mc = new MDPModelChecker(new Prism(new PrismDevNullLog()));
    BoundedNatBitSet fringe =
        NatBitSets.asBounded(stateCollapse.getExploredStates(), model.getNumStates()).complement();
    BitSet bitSet = mc.prob1(model, null, NatBitSets.toBitSet(fringe), true, null);
    NatBitSet prob1states = NatBitSets.asSet(bitSet);
    prob1states.retainAll(stateCollapse.getExploredStates());
    assert prob1states.intStream()
        .mapToDouble(bounds::getUpperBound)
        .allMatch(d -> PrismUtils.doublesAreEqual(d, 1.0d));
    prob1states.forEach((IntConsumer) stateCollapse::removeExploredState);
    prob1states.forEach((IntConsumer) bounds::clear);
    if (model instanceof MDPSimple) {
      prob1states.forEach((IntConsumer) ((MDPSimple) model)::clearState);
    }

    logger.log(Level.INFO, "Pruned {0} states", prob1states.size());
  }
   */
}
