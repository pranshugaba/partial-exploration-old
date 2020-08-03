package de.tum.in.pet.implementation.core;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.naturals.bitset.BitSets;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.RoaringNatBitSetFactory;
import de.tum.in.pet.implementation.reachability.PrismQuery;
import de.tum.in.pet.implementation.reachability.StateToIntTarget;
import de.tum.in.pet.implementation.reachability.ValueUpdate;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.BoundedSampler;
import de.tum.in.pet.sampler.BoundedStepFunction;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedSampler;
import de.tum.in.pet.sampler.UnboundedSamplerConfig;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.DefaultExplorer;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.CtmcEmbeddingGenerator;
import de.tum.in.probmodels.generator.CtmcUniformizingGenerator;
import de.tum.in.probmodels.generator.DtmcGenerator;
import de.tum.in.probmodels.generator.Generator;
import de.tum.in.probmodels.generator.MdpGenerator;
import de.tum.in.probmodels.graph.ComponentAnalyser;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.graph.SccComponentAnalyser;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.MarkovChain;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.util.PrismExpressionWrapper;
import de.tum.in.probmodels.util.PrismHelper;
import de.tum.in.probmodels.util.Util;
import explicit.CTMC;
import explicit.CTMCModelChecker;
import explicit.ConstructModel;
import explicit.DTMCModelChecker;
import explicit.ECComputer;
import explicit.MDPModelChecker;
import explicit.ModelCheckerResult;
import explicit.NondetModel;
import explicit.ProbModelChecker;
import explicit.SCCComputer;
import explicit.SCCConsumerStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
import org.json.JSONArray;
import org.json.JSONObject;
import parser.State;
import parser.Values;
import parser.ast.Expression;
import parser.ast.ExpressionTemporal;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
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

  private CoreChecker() {
    // Empty
  }

  public static void main(String... args) throws IOException, PrismException {
    NatBitSets.setFactory(new RoaringNatBitSetFactory());

    Option precisionOption = new Option(null, "precision", true, "Precision");
    Option completeOption = new Option(null, "complete", false, "Build complete model");
    Option completeAnalysisOption = new Option(null, "component-analysis", false,
        "Compute number and size of MECs / SCCs in built models");
    Option unboundedOption = new Option(null, "unbounded", false, "Build unbounded model");
    Option boundedOption = new Option(null, "bounded", true,
        "Build bounded model with given step bound");
    // Option iterative = new Option(null, "iterative", true, "Build complete model");
    Option validateOption = new Option(null, "validate", false, "Validate the core property");
    Option stabilityStepsOption = new Option(null, "stability", true,
        "Compute stability for given steps");
    Option heuristicOption = new Option(null, "heuristic", true, "Heuristic to use");
    Option modelFileOption = new Option("m", "model", true, "Path to model file");
    Option propertiesFileOption = new Option("p", "properties", true, "Path to properties file");
    Option constantsOption = new Option("c", "const", true, "Constants of model/property file, "
        + "comma separated list");
    Option uniformizationOption = new Option(null, "uniformization", true,
        "Uniformization rate for CTMC");
    Option boundedUpdateOption = new Option(null, "bounded-update", true, "The type of bounded "
        + "update to use (\"dense\", \"simple,n\")");
    Option jsonOutput = new Option(null, "output", true, "Output file");
    Option extrapolationProperty = new Option(null, "extrapolation-property", true,
        "A reachability query to extrapolate on the computed bounded core");
    Option extrapolationSteps = new Option(null, "extrapolation-steps", true,
        "The number of extrapolation steps of the reachability query");
    Option extrapolationComplete = new Option(null, "extrapolation-complete", false,
        "Compute extrapolation property on the complete model");

    modelFileOption.setRequired(true);

    Options options = new Options()
        .addOption(precisionOption)
        .addOption(completeOption)
        .addOption(completeAnalysisOption)
        .addOption(unboundedOption)
        .addOption(boundedOption)
        .addOption(validateOption)
        .addOption(stabilityStepsOption)
        .addOption(heuristicOption)
        .addOption(modelFileOption)
        .addOption(propertiesFileOption)
        .addOption(constantsOption)
        .addOption(uniformizationOption)
        .addOption(boundedUpdateOption)
        .addOption(jsonOutput)
        .addOption(extrapolationProperty)
        .addOption(extrapolationSteps)
        .addOption(extrapolationComplete);

    HelpFormatter formatter = new HelpFormatter();

    CommandLineParser cliParser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = cliParser.parse(options, args);
    } catch (ParseException e) {
      System.out.println("Failed to parse command line arguments: " + e.getMessage());
      formatter.printHelp("argument list", options);
      System.exit(1);
      throw new AssertionError(e);
    }

    boolean complete = commandLine.hasOption(completeOption.getLongOpt());
    boolean componentAnalysis = commandLine.hasOption(completeAnalysisOption.getLongOpt());
    boolean unbounded = commandLine.hasOption(unboundedOption.getLongOpt());
    boolean validateCoreProperty = commandLine.hasOption(validateOption.getLongOpt());
    double precision = commandLine.hasOption(precisionOption.getLongOpt())
        ? Double.parseDouble(commandLine.getOptionValue(precisionOption.getLongOpt()))
        : 1.0e-6;
    String uniformizationRate = commandLine.getOptionValue(uniformizationOption.getLongOpt(), "");
    Double ctmcUniformRate = uniformizationRate.isEmpty()
        ? null : Double.parseDouble(uniformizationRate);

    var heuristic = parseHeuristics(heuristicOption, commandLine);
    var boundedCore = parseOptionalIntOption(boundedOption, commandLine);
    var stabilitySteps = parseOptionalIntOption(stabilityStepsOption, commandLine);
    var boundedValues = parseBoundedValues(boundedUpdateOption, commandLine, precision, heuristic);

    String modelPath = commandLine.getOptionValue(modelFileOption.getLongOpt());
    @Nullable
    String constantsString = commandLine.getOptionValue(constantsOption.getLongOpt());
    @Nullable
    String propertiesFileString = commandLine.getOptionValue(propertiesFileOption.getLongOpt());

    var parse = PrismHelper.parse(modelPath, propertiesFileString, constantsString);
    ModulesFile modulesFile = parse.modulesFile();
    PropertiesFile propertiesFile = parse.propertiesFile();

    List<Expression> extrapolationExpressions;
    var extrapolationBound = parseOptionalIntOption(extrapolationSteps, commandLine);
    if (extrapolationBound.isPresent()) {
      if (commandLine.hasOption(extrapolationProperty.getLongOpt())) {
        assert propertiesFile != null;
        String propertyName = commandLine.getOptionValue(extrapolationProperty.getLongOpt());
        int index = propertiesFile.getPropertyIndexByName(propertyName);
        if (index == -1) {
          System.out.println("No property found for name " + propertyName);
          System.exit(1);
        }
        Expression expression = parse.expressions().get(index);
        extrapolationExpressions = Collections.singletonList(expression);
      } else {
        extrapolationExpressions = parse.expressions();
      }
    } else {
      extrapolationExpressions = List.of();
    }

    Values constantValues = parse.constants().getPFConstantValues();
    List<PrismQuery<?>> extrapolationQueries = new ArrayList<>();
    for (Expression expression : extrapolationExpressions) {
      PrismQuery<?> query = PrismQuery.parse(expression, constantValues, precision, false);
      checkArgument(query.expression().getOperator() == ExpressionTemporal.P_F);
      checkArgument(!query.isBounded());
      checkArgument(query.type().update() == ValueUpdate.MAX_VALUE);
      extrapolationQueries.add(query);
    }

    Prism prism = new Prism(new PrismDevNullLog());
    Prism mcPrism = new Prism(new PrismDevNullLog());
    mcPrism.getSettings().set(PrismSettings.PRISM_TERM_CRIT_PARAM, precision);

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);
    ModelType modelType = modulesFile.getModelType();
    ProbModelChecker mc;
    if (modelType == ModelType.MDP) {
      mc = new MDPModelChecker(mcPrism);
    } else if (modelType == ModelType.DTMC) {
      mc = new DTMCModelChecker(mcPrism);
    } else if (modelType == ModelType.CTMC) {
      mc = new CTMCModelChecker(mcPrism);
    } else {
      throw new IllegalArgumentException();
    }
    mc.setModulesFileAndPropertiesFile(modulesFile, null, generator);

    logger.log(Level.INFO, String.format("Running Core construction on model %s "
        + "with precision: %3.2g%n", modelPath, precision));

    JSONObject resultJson = new JSONObject();

    if (complete) {
      logger.log(Level.INFO, "Building complete model");

      Timer timer = new Timer();
      ConstructModel constructModel = new ConstructModel(mcPrism);
      explicit.Model model = constructModel.constructModel(generator);
      JSONObject modelJson = analyseModel(mcPrism, model, timer.finish(), componentAnalysis);

      resultJson.put("model", modelJson);
    }

    if (unbounded) {
      logger.log(Level.INFO, "Building unbounded core");

      JSONObject unboundedStats = new JSONObject();
      Timer timer = new Timer();
      AnnotatedModel<?> core = buildUnboundedCore(getExplorer(generator, ctmcUniformRate, true),
          new UnboundedCoreValues.Sparse(precision, heuristic));
      JSONObject modelJson = analyseModel(mcPrism, core.model, timer.finish(), componentAnalysis);
      modelJson.put("explored-states", core.exploredStates.size());
      unboundedStats.put(heuristic.toString(), modelJson);

      if (validateCoreProperty) {
        checkCoreProperty(precision, mc, core, -1);
      }

      resultJson.put("unbounded", unboundedStats);
    }

    if (extrapolationBound.isPresent()
        && commandLine.hasOption(extrapolationComplete.getLongOpt())) {
      int steps = extrapolationBound.getAsInt();

      Timer completeTimer = new Timer();
      var completeExplorer = getExplorer(new ModulesFileModelGenerator(modulesFile, prism),
          ctmcUniformRate, false);

      IntSet exploredStates = new IntOpenHashSet(completeExplorer.initialStates());
      IntStack stack = new IntArrayList(exploredStates);

      while (!stack.isEmpty()) {
        int state = stack.popInt();
        for (Distribution choice : completeExplorer.getChoices(state)) {
          for (int successor : choice.support()) {
            if (exploredStates.add(successor)) {
              assert !completeExplorer.isExploredState(state);
              completeExplorer.exploreState(successor);
              stack.push(successor);
            } else {
              assert completeExplorer.isExploredState(state);
            }
          }
        }
      }

      long time = completeTimer.finish();
      JSONObject extrapolationDetails = new JSONObject(Map.of("time", Timer.format(time),
          "states", exploredStates.size()));

      var completeModel = completeExplorer.model();
      int completeInitialState = completeModel.getInitialStates().iterator().nextInt();

      for (PrismQuery<?> query : extrapolationQueries) {
        JSONArray completeArray = new JSONArray();

        Timer completeExtrapolationTimer = new Timer();
        Expression right = query.expression().getOperand2();
        var predicate = new StateToIntTarget<>(new PrismExpressionWrapper(right),
            completeExplorer::getState);

        int completeStates = completeModel.getNumStates();
        double[] values = new double[completeStates];
        for (int state = 0; state < completeStates; state++) {
          if (predicate.test(state)) {
            values[state] = 1.0d;
          }
        }
        double[] newValues = Arrays.copyOf(values, values.length);

        completeArray.put(values[completeInitialState]);
        for (int step = 0; step < steps; step++) {
          double[] currentValues = values;
          for (int state = 0; state < completeStates; state++) {
            if (values[state] < 1.0d) {
              newValues[state] = completeModel.getChoices(state).stream()
                  .mapToDouble(d -> d.sumWeighted(i -> currentValues[i])).max().orElse(0.0d);
            }
          }
          completeArray.put(newValues[completeInitialState]);

          double[] swap = values;
          values = newValues;
          newValues = swap;
        }

        long completeTime = completeExtrapolationTimer.finish();
        extrapolationDetails.put(query.expression().toString(),
            Map.of("time", Timer.format(completeTime), "values", completeArray));
      }
      resultJson.put("extrapolation", extrapolationDetails);
    }

    JSONObject boundedStats = new JSONObject();
    if (boundedCore.isPresent()) {
      int stepBound = boundedCore.getAsInt();
      JSONObject stepBoundStats = new JSONObject();

      Timer timer = new Timer();
      var explorer = getExplorer(generator, ctmcUniformRate, false);
      var core = buildBoundedCore(stepBound, explorer, boundedValues.get());
      JSONObject modelJson = analyseModel(mcPrism, core.model, timer.finish(), componentAnalysis);
      modelJson.put("explored-states", core.exploredStates.size());
      stepBoundStats.put(heuristic.toString(), modelJson);
      boundedStats.put(String.valueOf(stepBound), stepBoundStats);

      if (validateCoreProperty) {
        checkCoreProperty(precision, mc, core, stepBound);
      }

      if (stabilitySteps.isPresent()) {
        int initialState = core.model.getInitialStates().iterator().nextInt();
        JSONArray probabilities = new JSONArray();

        Timer approximationTimer = new Timer();
        int bound = stabilitySteps.getAsInt();
        int states = core.model.getNumStates();
        double[] bounds = new double[states];
        for (int s = 0; s < states; s++) {
          if (!core.exploredStates.contains(s)) {
            bounds[s] = 1.0d;
          }
        }
        double[] newBounds = Arrays.copyOf(bounds, bounds.length);

        probabilities.put(bounds[initialState]);
        for (int i = 0; i <= bound; i++) {
          IntIterator iterator = core.exploredStates.iterator();

          double[] currentBounds = bounds;
          while (iterator.hasNext()) {
            int state = iterator.nextInt();
            newBounds[state] = core.model.getChoices(state).stream()
                .mapToDouble(d -> d.sumWeighted(s -> currentBounds[s])).max().orElse(0.0d);
          }
          probabilities.put(newBounds[initialState]);
          double[] swap = bounds;
          bounds = newBounds;
          newBounds = swap;
        }

        long time = approximationTimer.finish();
        modelJson.put("stability", new JSONObject(
            Map.of("time", Timer.format(time), "probability", probabilities)));
      }

      if (extrapolationBound.isPresent()) {
        JSONObject extrapolationMap = new JSONObject();
        int steps = extrapolationBound.getAsInt();

        for (PrismQuery<?> query : extrapolationQueries) {
          int initialState = core.model.getInitialStates().iterator().nextInt();
          JSONArray lowerArray = new JSONArray();
          JSONArray upperArray = new JSONArray();

          Timer extrapolationTimer = new Timer();
          Expression right = query.expression().getOperand2();
          var predicate =
              new StateToIntTarget<>(new PrismExpressionWrapper(right), explorer::getState);

          int states = core.model.getNumStates();
          double[] upper = new double[states];
          double[] lower = new double[states];
          NatBitSet unknownStates = NatBitSets.set();
          for (int state = 0; state < states; state++) {
            if (core.exploredStates.contains(state)) {
              if (predicate.test(state)) {
                lower[state] = 1.0d;
                upper[state] = 1.0d;
              } else {
                unknownStates.add(state);
              }
            } else {
              upper[state] = 1.0d;
            }
          }
          double[] newUpper = Arrays.copyOf(upper, upper.length);
          double[] newLower = Arrays.copyOf(lower, lower.length);

          lowerArray.put(lower[initialState]);
          upperArray.put(upper[initialState]);
          for (int step = 0; step < steps; step++) {
            IntIterator iterator = unknownStates.iterator();
            double[] currentLower = lower;
            double[] currentUpper = upper;
            while (iterator.hasNext()) {
              int state = iterator.nextInt();
              List<Distribution> choices = core.model.getChoices(state);
              if (lower[state] < 1.0d) {
                newLower[state] = choices.stream()
                    .mapToDouble(d -> d.sumWeighted(i -> currentLower[i])).max().orElse(0.0d);
                newUpper[state] = choices.stream()
                    .mapToDouble(d -> d.sumWeighted(i -> currentUpper[i])).max().orElse(0.0d);
              }
            }
            lowerArray.put(newLower[initialState]);
            upperArray.put(newUpper[initialState]);

            double[] swapLower = newLower;
            newLower = lower;
            lower = swapLower;
            double[] swapUpper = newUpper;
            newUpper = upper;
            upper = swapUpper;
          }
          long time = extrapolationTimer.finish();

          extrapolationMap.put(query.expression().toString(), Map.of("time", Timer.format(time),
              "lower", lowerArray, "upper", upperArray));
        }
        if (!extrapolationMap.isEmpty()) {
          modelJson.put("extrapolation", extrapolationMap);
        }
      }
    }
    if (!boundedStats.isEmpty()) {
      resultJson.put("bounded", boundedStats);
    }

    String outputDestination = commandLine.getOptionValue(jsonOutput.getLongOpt(), "-");
    if (outputDestination.equals("-")) {
      System.out.println(resultJson.toString(2));
    } else {
      try (BufferedWriter writer = Files.newBufferedWriter(Path.of(outputDestination),
          StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
        //noinspection resource
        resultJson.write(writer);
      } catch (IOException e) {
        System.out.println(resultJson);
        throw e;
      }
    }
  }

  private static JSONObject analyseModel(Prism prism, explicit.Model model, long constructionTime,
      boolean analyseComponents) throws PrismException {
    JSONObject modelJson = new JSONObject();
    modelJson.put("states", model.getNumStates());
    modelJson.put("transitions", model.getNumTransitions());
    modelJson.put("time", Timer.format(constructionTime));
    if (analyseComponents) {
      modelJson.put("components", analyseComponents(prism, model));
    }
    return modelJson;
  }

  private static JSONObject analyseComponents(Prism prism, explicit.Model model)
      throws PrismException {
    JSONObject componentJson = new JSONObject();

    Timer componentTimer = new Timer();
    List<BitSet> components;
    if (model instanceof NondetModel) {
      ECComputer ecComputer = ECComputer.createECComputer(prism, (NondetModel) model);
      ecComputer.computeMECStates();
      components = ecComputer.getMECStates();
    } else {
      SCCConsumerStore store = new SCCConsumerStore();
      SCCComputer computer = SCCComputer.createSCCComputer(prism, model, store);
      computer.computeSCCs(false);
      components = store.getSCCs();
    }
    componentJson.put("time", Timer.format(componentTimer.finish()));
    if (components.isEmpty()) {
      componentJson.put("count", 0);
      componentJson.put("maximum-size", 0);
      componentJson.put("minimum-size", 0);
      componentJson.put("average-size", 0);
      componentJson.put("sum-size", 0);
    } else {
      IntSummaryStatistics statistics = components.stream()
          .mapToInt(BitSet::cardinality)
          .summaryStatistics();
      componentJson.put("count", statistics.getCount());
      componentJson.put("maximum-size", statistics.getMax());
      componentJson.put("minimum-size", statistics.getMin());
      componentJson.put("average-size", statistics.getAverage());
      componentJson.put("sum-size", statistics.getSum());
    }
    return componentJson;
  }

  private static SuccessorHeuristic parseHeuristics(Option option, CommandLine commandLine) {
    if (!commandLine.hasOption(option.getLongOpt())) {
      return SuccessorHeuristic.WEIGHTED;
    }

    String heuristicString = commandLine.getOptionValue(option.getLongOpt());
    try {
      return SuccessorHeuristic.valueOf(heuristicString);
    } catch (IllegalArgumentException e) {
      String values = Arrays.stream(SuccessorHeuristic.values())
          .map(Object::toString).collect(Collectors.joining(", "));
      System.out.printf("Unknown heuristic %s. Possible values are: %s%n", heuristicString, values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  private static OptionalInt parseOptionalIntOption(Option option, CommandLine commandLine) {
    if (!commandLine.hasOption(option.getLongOpt())) {
      return OptionalInt.empty();
    }
    String optionValue = commandLine.getOptionValue(option.getLongOpt());
    int stepBound;
    try {
      stepBound = Integer.parseInt(optionValue);
    } catch (NumberFormatException e) {
      System.out.printf("Invalid number %s", optionValue);
      System.exit(1);
      throw new AssertionError(e);
    }
    if (stepBound <= 0) {
      System.out.println("Step bound must be larger than 0");
      System.exit(1);
    }
    return OptionalInt.of(stepBound);
  }

  private static Supplier<BoundedCoreValues> parseBoundedValues(Option option,
      CommandLine commandLine, double precision, SuccessorHeuristic heuristic) {
    if (!commandLine.hasOption(option.getLongOpt())) {
      return () -> new BoundedCoreValues.Simple(precision, heuristic, 5);
    }

    String[] split = commandLine.getOptionValue(option.getLongOpt()).split(",");
    if (split[0].equals("dense")) {
      return () -> new BoundedCoreValues.Dense(precision, heuristic);
    }
    if (split[0].equals("simple")) {
      int count = Integer.parseInt(split[1]);
      return () -> new BoundedCoreValues.Simple(precision, heuristic, count);
    }
    System.out.println("Invalid state update");
    System.exit(1);
    throw new AssertionError();
  }

  private static AnnotatedModel<?> buildUnboundedCore(Explorer<State, Model> explorer,
      UnboundedCoreValues values) throws PrismException {
    ComponentAnalyser analyser;
    if (explorer.model() instanceof MarkovChain) {
      analyser = new SccComponentAnalyser();
    } else if (explorer.model() instanceof MarkovDecisionProcess) {
      analyser = new MecComponentAnalyser();
    } else {
      throw new IllegalArgumentException(explorer.model().getClass().toString());
    }
    logger.log(Level.INFO, "Building unbounded core, explorer {0}, values {1}, analyser {2}",
        new Object[] {explorer, values, analyser});

    var config = UnboundedSamplerConfig.getDefault();
    var sampler = new UnboundedSampler<>(explorer, analyser, values, config);
    sampler.run();
    return sampler.model();
  }

  private static Explorer<State, Model> getExplorer(ModelGenerator generator,
      Double ctmcUniformRate, boolean removeSelfLoops) {
    ModelType modelType = generator.getModelType();
    if (modelType == ModelType.MDP) {
      MdpGenerator mdpGenerator = new MdpGenerator(generator);
      return DefaultExplorer.of(new MarkovDecisionProcess(), mdpGenerator, removeSelfLoops);
    }
    if (modelType == ModelType.DTMC) {
      DtmcGenerator dtmcGenerator = new DtmcGenerator(generator);
      return DefaultExplorer.of(new MarkovChain(), dtmcGenerator, removeSelfLoops);
    }
    if (modelType == ModelType.CTMC) {
      Generator<State> stateGenerator = ctmcUniformRate == null
          ? new CtmcEmbeddingGenerator(generator)
          : new CtmcUniformizingGenerator(generator, ctmcUniformRate);
      return DefaultExplorer.of(new MarkovChain(), stateGenerator, removeSelfLoops);
    } else {
      throw new IllegalArgumentException();
    }
  }

  private static AnnotatedModel<?> buildBoundedCore(int stepBound, Explorer<State, Model> explorer,
      BoundedCoreValues values) throws PrismException {
    BoundedStepFunction stepFunction = (state, remaining, choices, bounds) -> {
      assert remaining > 0;
      double maximum = 0.0d;
      for (Distribution choice : choices) {
        double value = choice.sumWeighted(s -> bounds[s].upperBound());
        if (value > maximum) {
          maximum = value;
        }
      }
      return Bounds.reach(0.0d, maximum);
    };
    logger.log(Level.INFO, "Building {0}-bounded core, explorer {1}, values {2}",
        new Object[] {stepBound, explorer, values});

    var builder = new BoundedSampler<>(explorer, stepBound, values, stepFunction);
    builder.run();
    return builder.model();
  }

  private static ModelCheckerResult computeFringeReachability(ProbModelChecker mc,
      AnnotatedModel<?> partialModel, int stepBound) throws PrismException {
    BitSet target = BitSets.of(partialModel.getFringeStates());
    Model model = partialModel.model;

    if (model instanceof MarkovChain) {
      DTMCModelChecker dtmcChecker = (mc instanceof DTMCModelChecker)
          ? (DTMCModelChecker) mc
          : new DTMCModelChecker(mc);
      MarkovChain chain = (MarkovChain) model;
      return stepBound < 0
          ? dtmcChecker.computeReachProbs(chain, target)
          : dtmcChecker.computeBoundedReachProbs(chain, target, stepBound);
    }
    if (model instanceof MarkovDecisionProcess) {
      MDPModelChecker mdpChecker = (MDPModelChecker) mc;
      MarkovDecisionProcess process = (MarkovDecisionProcess) model;
      return stepBound < 0
          ? mdpChecker.computeReachProbs(process, target, false)
          : mdpChecker.computeBoundedReachProbs(process, target, stepBound, false);
    }
    throw new IllegalArgumentException();
  }

  private static void checkCoreProperty(double precision, ProbModelChecker mc,
      AnnotatedModel<?> partialModel, int stepBound) throws PrismException {
    logger.log(Level.INFO, "Checking core property");
    int initialState = partialModel.model.getFirstInitialState();
    ModelCheckerResult result = computeFringeReachability(mc, partialModel, stepBound);
    double reach = result.soln[initialState];
    logger.log(Level.INFO, () -> String.format("Reachability: %f", reach));
    if (!Util.lessOrEqual(reach, precision)) {
      throw new PrismException("Core property violated!");
    }
  }

  public static void computeUniformisationRate(String[] args) throws PrismException, IOException {
    String modelPath = args[0];
    @Nullable
    String constantsString = args.length == 2 ? args[1] : null;

    PrismHelper.PrismParseResult parse = PrismHelper.parse(modelPath, null, constantsString);
    ModulesFile modulesFile = parse.modulesFile();

    Prism prism = new Prism(new PrismDevNullLog());
    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);
    ModelType modelType = modulesFile.getModelType();
    checkArgument(modelType == ModelType.CTMC);
    ConstructModel constructModel = new ConstructModel(prism);
    CTMC completeModel = (CTMC) constructModel.constructModel(generator);
    System.out.printf("%.2f%n", completeModel.getDefaultUniformisationRate());
  }

  private static final class Timer {
    private final long time;

    public Timer() {
      this.time = System.nanoTime();
    }

    public long finish() {
      return System.nanoTime() - time;
    }

    private static String format(long time) {
      return String.format("%.2f", time / (double) TimeUnit.SECONDS.toNanos(1L));
    }
  }
}
