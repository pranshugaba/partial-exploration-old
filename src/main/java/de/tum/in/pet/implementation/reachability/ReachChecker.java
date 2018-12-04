package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static de.tum.in.pet.util.PrismHelper.parse;

import de.tum.in.naturals.set.DefaultNatBitSetFactory;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.explorer.DefaultDTMCExplorer;
import de.tum.in.pet.explorer.DefaultMDPExplorer;
import de.tum.in.pet.explorer.EmbeddingCTMCExplorer;
import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.graph.MecComponentAnalyser;
import de.tum.in.pet.graph.SccComponentAnalyser;
import de.tum.in.pet.model.DTMC;
import de.tum.in.pet.model.MDP;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedSampler;
import de.tum.in.pet.util.CliHelper;
import de.tum.in.pet.util.PrismHelper.PrismParseResult;
import de.tum.in.pet.util.SamplingInstance;
import de.tum.in.pet.util.Util;
import de.tum.in.pet.values.StateInterpretation;
import de.tum.in.pet.values.StateValues;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.State;
import parser.ast.Expression;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
import prism.ModelGenerator;
import prism.ModelType;
import prism.Prism;
import prism.PrismDevNullLog;
import prism.PrismException;
import simulator.ModulesFileModelGenerator;

@SuppressWarnings("PMD")
public final class ReachChecker {
  private static final Logger logger = Logger.getLogger(ReachChecker.class.getName());

  private ReachChecker() {}

  public static void main(String... args) throws IOException, PrismException {
    Option precisionOption = new Option(null, "precision", true, "Precision");
    Option heuristicOption = CliHelper.getDefaultHeuristicOption();
    Option modelOption = new Option("m", "model", true, "Path to model file");
    Option propertiesOption = new Option("p", "properties", true, "Path to properties file");
    Option propertyNameOption = new Option(null, "property", true, "Name of property to check");
    Option constantsOption = new Option("c", "const", true,
        "Constants of model/property file, comma separated list");
    Option expectedValuesOption = new Option(null, "expected", true,
        "Comma separated list of the true values of the properties");
    Option onlyPrintResultOption = new Option(null, "only-result", false,
        "Only print result");
    Option relativeErrorOption = new Option(null, "relative-error", false,
        "Use relative error estimate");

    modelOption.setRequired(true);
    propertiesOption.setRequired(true);

    Options options = new Options()
        .addOption(precisionOption)
        .addOption(heuristicOption)
        .addOption(modelOption)
        .addOption(propertiesOption)
        .addOption(propertyNameOption)
        .addOption(expectedValuesOption)
        .addOption(constantsOption)
        .addOption(onlyPrintResultOption)
        .addOption(relativeErrorOption);

    CommandLine commandLine = CliHelper.parse(options, args);

    boolean relativeError = commandLine.hasOption(relativeErrorOption.getLongOpt());
    double precision = commandLine.hasOption(precisionOption.getLongOpt())
        ? Double.parseDouble(commandLine.getOptionValue(precisionOption.getLongOpt()))
        : Util.DEFAULT_PRECISION;

    SuccessorHeuristic heuristic = CliHelper.parseHeuristic(
        commandLine.getOptionValue(heuristicOption.getLongOpt()), SuccessorHeuristic.WEIGHTED);

    NatBitSets.setFactory(new DefaultNatBitSetFactory((a, b) -> true));
    // NatBitSets.setFactory(new RoaringNatBitSetFactory());

    PrismParseResult parse = parse(commandLine, modelOption, propertiesOption, constantsOption);
    ModulesFile modulesFile = parse.modulesFile();
    PropertiesFile propertiesFile = checkNotNull(parse.propertiesFile());

    Prism prism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);
    ModelType modelType = modulesFile.getModelType();

    List<Expression> parsedExpressions = parse.expressions();
    List<ReachabilityQuery> expressions = new ArrayList<>();
    if (commandLine.hasOption(propertyNameOption.getLongOpt())) {
      String propertyName = commandLine.getOptionValue(propertyNameOption.getLongOpt());
      int index = propertiesFile.getPropertyIndexByName(propertyName);
      ReachabilityQuery query = ReachabilityQueryUtil.create(parsedExpressions.get(index),
          parse.constants().getPFConstantValues(), precision, relativeError);
      expressions.add(query);
    } else {
      for (Expression expression : parsedExpressions) {
        expressions.add(ReachabilityQueryUtil.create(expression,
            parse.constants().getPFConstantValues(), precision, relativeError));
      }
    }

    List<String> expressionExpected;
    if (commandLine.hasOption(expectedValuesOption.getLongOpt())) {
      String expectedValuesString = commandLine.getOptionValue(expectedValuesOption.getLongOpt());
      expressionExpected = Arrays.stream(expectedValuesString.split(","))
          .collect(Collectors.toList());
      if (expressions.size() != expressionExpected.size()) {
        throw new IllegalArgumentException("Differing lengths");
      }
    } else {
      expressionExpected = Collections.emptyList();
    }

    checkArgument(!expressions.isEmpty(), "No valid expression found");
    SamplingProvider samplingSupplier;

    if (modelType == ModelType.MDP) {
      samplingSupplier = query -> {
        Explorer<MDP> explorer = new DefaultMDPExplorer(generator);
        StateValues stateValues = new StateValuesUnboundedReachability();
        StateUpdateReachability stateUpdate =
            new StateUpdateReachability(explorer::getState, query.predicate(), query.updateType());
        UnboundedSampler<MDP> unboundedBuilder = new UnboundedSampler<>(explorer, stateValues,
            heuristic, stateUpdate, stateUpdate, query.verdict(), new MecComponentAnalyser());

        return SamplingInstance.of(unboundedBuilder, stateValues, stateUpdate);
      };
    } else if (modelType == ModelType.DTMC) {
      samplingSupplier = query -> {
        Explorer<DTMC> explorer = new DefaultDTMCExplorer(generator);
        StateValues stateValues = new StateValuesUnboundedReachability();
        StateUpdateReachability stateUpdate =
            new StateUpdateReachability(explorer::getState, query.predicate(), query.updateType());
        UnboundedSampler<DTMC> sampler = new UnboundedSampler<>(explorer, stateValues,
            heuristic, stateUpdate, stateUpdate, query.verdict(), new SccComponentAnalyser());

        return SamplingInstance.of(sampler, stateValues, stateUpdate);
      };
    } else if (modelType == ModelType.CTMC) {
      samplingSupplier = query -> {
        Explorer<DTMC> explorer = new EmbeddingCTMCExplorer(generator);
        StateValues stateValues = new StateValuesUnboundedReachability();
        StateUpdateReachability stateUpdate =
            new StateUpdateReachability(explorer::getState, query.predicate(), query.updateType());
        UnboundedSampler<DTMC> sampler = new UnboundedSampler<>(explorer, stateValues,
            heuristic, stateUpdate, stateUpdate, query.verdict(), new SccComponentAnalyser());

        return SamplingInstance.of(sampler, stateValues, stateUpdate);
      };
    } else {
      System.out.println("Model type " + modelType + " not supported");
      System.exit(1);
      throw new AssertionError();
    }

    ListIterator<ReachabilityQuery> iterator = expressions.listIterator();
    while (iterator.hasNext()) {
      int index = iterator.nextIndex();
      ReachabilityQuery expression = iterator.next();
      logger.log(Level.INFO, "Checking expression {0}", expression);
      SamplingInstance<?> instance = samplingSupplier.build(expression);
      instance.sampler().build();

      Explorer<?> explorer = instance.sampler().explorer();
      IntCollection initialStates = explorer.initialStates();
      StateValues stateValues = instance.stateValues();
      StateInterpretation interpretation = expression.interpretation();
      if (initialStates.size() > 1) {
        for (int initialState : initialStates) {
          State state = explorer.getState(initialState);
          Object value = interpretation.interpret(initialState, stateValues.bounds(initialState));
          String format = value instanceof Double
              ? "%s %.10g%n" : "%s %s%n";
          System.out.printf(format, state, value);
        }
      } else {
        int initialState = initialStates.iterator().nextInt();
        Object value = interpretation.interpret(initialState, stateValues.bounds(initialState));
        String format = value instanceof Double
            ? "%.10g%n" : "%s%n";
        System.out.printf(format, value);
      }

      if (!expressionExpected.isEmpty()) {
        if (initialStates.size() != 1) {
          throw new IllegalArgumentException();
        }
        int initialState = initialStates.iterator().nextInt();

        Object value = interpretation.interpret(initialState, stateValues.bounds(initialState));
        if (value instanceof Boolean) {
          boolean expected = Boolean.parseBoolean(expressionExpected.get(index));
          if (expected != (Boolean) value) {
            System.out.printf("Expected %s, got %s", expected, value);
            System.exit(1);
          }
        } else if (value instanceof Double) {
          double computed = (Double) value;
          double expected = Double.parseDouble(expressionExpected.get(index));
          if (relativeError) {
            double expectedLower = (1 - precision) * expected;
            double expectedUpper = (1 + precision) * expected;
            if (computed < expectedLower || computed > expectedUpper) {
              System.out.printf("Expected in range [%.6g, %.6g] but got %.6g, "
                      + "actual precision %.6g > required precision %.6g%n",
                  expectedLower, expectedUpper, computed,
                  Math.abs(1 - computed / expected), precision);
              System.exit(1);
            } else {
              System.out.printf("Expected in range [%.6g, %.6g], got %.6g, "
                      + "actual precision: %.6g%n",
                  computed, expectedLower, expectedUpper, Math.abs(1 - computed / expected));
            }
          } else {
            double difference = Math.abs(expected - computed);
            if (difference >= precision) {
              System.out.printf("Expected %.6g but got %.6g (difference %.6g > precision %.6g%n",
                  expected, computed, difference, precision);
              System.exit(1);
            } else {
              System.out.printf("Expected %.6g, got %.6g, actual precision: %.6g%n",
                  expected, computed, difference);
            }
          }
        }
      }
    }
  }

  @FunctionalInterface
  interface SamplingProvider {
    SamplingInstance<?> build(ReachabilityQuery query) throws PrismException;
  }
}
