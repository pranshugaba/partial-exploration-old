package de.tum.in.prism.core;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.bounds.StateBoundsDense;
import de.tum.in.prism.core.bounds.StateUpdateCore;
import de.tum.in.prism.core.builder.AnnotatedModel;
import de.tum.in.prism.core.builder.BoundedDTMCCoreIterativeBuilder;
import de.tum.in.prism.core.builder.BoundedSamplingBuilder;
import de.tum.in.prism.core.builder.UnboundedSamplingBuilder;
import de.tum.in.prism.core.explorer.CollapsingDTMCExplorer;
import de.tum.in.prism.core.explorer.CollapsingMDPExplorer;
import de.tum.in.prism.core.util.Util;
import explicit.ConstructModel;
import explicit.DTMC;
import explicit.DTMCModelChecker;
import explicit.MDP;
import explicit.MDPModelChecker;
import explicit.Model;
import explicit.ModelCheckerResult;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import parser.PrismParser;
import parser.ast.Expression;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
import prism.ModelGenerator;
import prism.ModelType;
import prism.Prism;
import prism.PrismDevNullLog;
import prism.PrismException;
import prism.PrismLog;
import prism.PrismPrintStreamLog;
import prism.PrismSettings;
import prism.Result;
import simulator.ModulesFileModelGenerator;

public class CoreChecker {
  private static final Logger logger = Logger.getLogger(CoreChecker.class.getName());

  public static void main(String... args) throws IOException, PrismException {
    if (args.length != 2) {
      System.exit(1);
    }

    PrismParser parser = new PrismParser();
    String modelPath = args[0];
    String propertyPath = args[1];

    logger.log(Level.INFO, "Reading model from {0} and properties from {1}",
        new Object[] {modelPath, propertyPath});
    ModulesFile modulesFile;
    try (InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(modelPath)))) {
      modulesFile = parser.parseModulesFile(in, null);
    }
    modulesFile.tidyUp();

    if (!modulesFile.getUndefinedConstants().isEmpty()) {
      logger.log(Level.SEVERE, "There are some undefined constants {0}",
          modulesFile.getUndefinedConstants());
      System.exit(1);
    }

    PropertiesFile propertiesFile;
    try (InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(propertyPath)))) {
      propertiesFile = parser.parsePropertiesFile(modulesFile, in);
    }
    propertiesFile.tidyUp();

    Prism prism = new Prism(new PrismPrintStreamLog(System.out));
    prism.getLog().setVerbosityLevel(PrismLog.VL_ALL);

    Prism mcPrism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);

    double precision = 1e-10;
    int stepBound = 10;
    mcPrism.getSettings().set(PrismSettings.PRISM_TERM_CRIT_PARAM, precision);

    boolean complete = true;
    boolean unbounded = true;
    boolean bounded = true;
    boolean validateCoreProperty = true;
    Map<String, Integer> sizes = new HashMap<>();
    Multimap<Expression, AnnotatedResult> results = HashMultimap.create();

    logger.log(Level.INFO, String.format("Core Precision: %3.2g%n", precision));

    List<Expression> expressions = new ArrayList<>();
    for (int i = 0; i < propertiesFile.getNumProperties(); i++) {
      expressions.add(propertiesFile.getProperty(i));
    }

    Model completeModel = null;

    if (complete) {
      logger.log(Level.INFO, "Building complete model");
      ConstructModel constructModel = new ConstructModel(mcPrism);
      completeModel = constructModel.constructModel(generator);
      sizes.put("complete", completeModel.getNumStates());
      logger.log(Level.INFO, "Size: {0}", completeModel.getNumStates());
    }

    if (modulesFile.getModelType() == ModelType.MDP) {
      MDPModelChecker mc = new MDPModelChecker(mcPrism);
      mc.setModulesFileAndPropertiesFile(modulesFile, propertiesFile, generator);

      if (complete) {
        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0}", expression);
          Result result = mc.check(completeModel, expression);
          results.put(expression, new AnnotatedResult("complete", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }
      }

      if (unbounded) {
        logger.log(Level.INFO, "Building unbounded MDP core");

        CollapsingMDPExplorer explorer = new CollapsingMDPExplorer(generator);
        StateUpdateCore stateUpdate = new StateUpdateCore(new StateBoundsDense());
        UnboundedSamplingBuilder.MDPBuilder unboundedBuilder =
            new UnboundedSamplingBuilder.MDPBuilder(prism, explorer, stateUpdate);
        AnnotatedModel<MDP> unboundedPartial = unboundedBuilder.build(precision);
        unboundedPartial.model.findDeadlocks(true);
        sizes.put("unbounded", unboundedPartial.exploredStates.size());
        logger.log(Level.INFO, "Size: {0}", unboundedPartial.exploredStates.size());

        if (validateCoreProperty) {
          logger.log(Level.INFO, "Checking core property");

          BitSet unboundedTarget = NatBitSets.toBitSet(unboundedPartial.getFringeStates());
          ModelCheckerResult unboundedReach =
              mc.computeReachProbs(unboundedPartial.model, unboundedTarget, false);

          int unboundedInitialState = unboundedPartial.model.getFirstInitialState();
          logger.log(Level.INFO, "Reachability: {0}", unboundedReach.soln[unboundedInitialState]);
          if (!Util.doublesAreLessOrEqual(unboundedReach.soln[unboundedInitialState], precision)) {
            throw new PrismException("Core property violated!");
          }
        }

        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0} on core", expression);
          Result result = mc.check(unboundedPartial.model, expression);
          results.put(expression, new AnnotatedResult("unbounded", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }
      }

      if (bounded) {
        logger.log(Level.INFO, "Building bounded sampling MDP core");

        BoundedSamplingBuilder.MDP boundedBuilder =
            new BoundedSamplingBuilder.MDP(prism, generator, stepBound, precision);
        AnnotatedModel<MDP> boundedPartial = boundedBuilder.build();
        boundedPartial.model.findDeadlocks(true);
        sizes.put("bounded", boundedPartial.exploredStates.size());
        logger.log(Level.INFO, "Size: {0}", boundedPartial.exploredStates.size());

        if (validateCoreProperty) {
          logger.log(Level.INFO, "Checking bounded core property");

          BitSet boundedTarget = NatBitSets.toBitSet(boundedPartial.getFringeStates());
          ModelCheckerResult boundedReach =
              mc.computeBoundedReachProbs(boundedPartial.model, boundedTarget, stepBound, false);

          int boundedInitialState = boundedPartial.model.getFirstInitialState();
          logger.log(Level.INFO, "Reachability: {0}", boundedReach.soln[boundedInitialState]);
          if (!Util.doublesAreLessOrEqual(boundedReach.soln[boundedInitialState], precision)) {
            throw new PrismException("Core property violated!");
          }
        }

        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0} on step bounded core", expression);
          Result result = mc.check(boundedPartial.model, expression);
          results.put(expression, new AnnotatedResult("bounded", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }
      }
    } else if (modulesFile.getModelType() == ModelType.DTMC) {
      DTMCModelChecker mc = new DTMCModelChecker(mcPrism);
      mc.setModulesFileAndPropertiesFile(modulesFile, propertiesFile, generator);

      if (complete) {
        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0}", expression);
          Result result = mc.check(completeModel, expression);
          results.put(expression, new AnnotatedResult("complete", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }
      }

      if (unbounded) {
        logger.log(Level.INFO, "Building unbounded DTMC core");

        CollapsingDTMCExplorer explorer = new CollapsingDTMCExplorer(generator);
        StateUpdateCore stateUpdate = new StateUpdateCore(new StateBoundsDense());
        UnboundedSamplingBuilder.DTMCBuilder unboundedSamplingBuilder =
            new UnboundedSamplingBuilder.DTMCBuilder(prism, explorer, stateUpdate);
        AnnotatedModel<DTMC> unboundedPartial = unboundedSamplingBuilder.build(precision);
        unboundedPartial.model.findDeadlocks(true);
        sizes.put("unbounded", unboundedPartial.exploredStates.size());
        logger.log(Level.INFO, "Size: {0}", unboundedPartial.exploredStates.size());

        if (validateCoreProperty) {
          logger.log(Level.INFO, "Checking core property");

          BitSet unboundedTarget = NatBitSets.toBitSet(unboundedPartial.getFringeStates());
          ModelCheckerResult unboundedReach = mc
              .computeReachProbs(unboundedPartial.model, unboundedTarget);

          int unboundedInitialState = unboundedPartial.model.getFirstInitialState();
          logger.log(Level.INFO, "Reachability: {0}", unboundedReach.soln[unboundedInitialState]);
          if (!Util.doublesAreLessOrEqual(unboundedReach.soln[unboundedInitialState], precision)) {
            throw new PrismException("Core property violated!");
          }
        }

        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0} on core", expression);
          Result result = mc.check(unboundedPartial.model, expression);
          results.put(expression, new AnnotatedResult("unbounded", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }
      }

      if (bounded) {
        logger.log(Level.INFO, "Building bounded iterative DTMC core");

        BoundedDTMCCoreIterativeBuilder boundedIterativeBuilder =
            new BoundedDTMCCoreIterativeBuilder(prism, generator, stepBound, precision);
        AnnotatedModel<DTMC> boundedIterativePartial = boundedIterativeBuilder.build();
        boundedIterativePartial.model.findDeadlocks(true);
        sizes.put("bounded iterative", boundedIterativePartial.exploredStates.size());
        logger.log(Level.INFO, "Size: {0}", boundedIterativePartial.exploredStates.size());

        if (validateCoreProperty) {
          logger.log(Level.INFO, "Checking core property");

          BitSet boundedIterativeTarget =
              NatBitSets.toBitSet(boundedIterativePartial.getFringeStates());
          ModelCheckerResult boundedIterativeReach =
              mc.computeReachProbs(boundedIterativePartial.model, boundedIterativeTarget);

          int boundedInitialState = boundedIterativePartial.model.getFirstInitialState();
          logger.log(Level.INFO, "Reachability: {0}",
              boundedIterativeReach.soln[boundedInitialState]);
          if (!Util
              .doublesAreLessOrEqual(boundedIterativeReach.soln[boundedInitialState], precision)) {
            throw new PrismException("Core property violated!");
          }
        }

        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0} on bounded iterative core", expression);
          Result result = mc.check(boundedIterativePartial.model, expression);
          results.put(expression, new AnnotatedResult("bounded iterative", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }

        logger.log(Level.INFO, "Building bounded sampling DTMC core");
        BoundedSamplingBuilder.DTMC boundedSamplingBuilder =
            new BoundedSamplingBuilder.DTMC(prism, generator, stepBound, precision);
        AnnotatedModel<DTMC> boundedSamplingPartial = boundedSamplingBuilder.build();
        boundedSamplingPartial.model.findDeadlocks(true);
        sizes.put("bounded sampling", boundedSamplingPartial.exploredStates.size());
        logger.log(Level.INFO, "Size: {0}", boundedSamplingPartial.exploredStates.size());

        if (validateCoreProperty) {
          logger.log(Level.INFO, "Checking core property");

          BitSet boundedSamplingTarget =
              NatBitSets.toBitSet(boundedSamplingPartial.getFringeStates());
          ModelCheckerResult boundedSamplingReach =
              mc.computeReachProbs(boundedSamplingPartial.model, boundedSamplingTarget);

          int boundedInitialState = boundedIterativePartial.model.getFirstInitialState();
          logger.log(Level.INFO, "Reachability: {0}",
              boundedSamplingReach.soln[boundedInitialState]);
          if (!Util.doublesAreLessOrEqual(boundedSamplingReach.soln[boundedInitialState],
              precision)) {
            throw new PrismException("Core property violated!");
          }
        }

        for (Expression expression : expressions) {
          logger.log(Level.INFO, "Checking expression {0} on bounded sampling core", expression);
          Result result = mc.check(boundedIterativePartial.model, expression);
          results.put(expression, new AnnotatedResult("bounded sampling", result));
          logger.log(Level.INFO, "Got: {0}", result);
        }
      }
    }

    StringBuilder resultString = new StringBuilder("Results:\n");
    sizes.forEach((type, size) -> resultString.append(" ").append(type).append(": ")
        .append(size).append('\n'));
    resultString.append('\n');

    results.asMap().forEach((expression, annotatedResults) -> {
      resultString.append(expression).append("\n");
      List<AnnotatedResult> orderedResults = new ArrayList<>(annotatedResults);
      orderedResults.sort(Comparator.comparing(a -> a.name));
      orderedResults.forEach(result -> resultString.append("  ").append(result.name)
          .append(": ").append(result.result).append('\n'));
      resultString.append("\n");
    });
    System.out.println(resultString);
  }

  private static final class AnnotatedResult {
    public final String name;
    public final Result result;

    public AnnotatedResult(String name, Result result) {
      this.name = name;
      this.result = result;
    }
  }
}
