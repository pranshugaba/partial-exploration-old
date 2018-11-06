package de.tum.in.prism.core.util;

import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.unionfind.IntUnionFind;
import de.tum.in.prism.core.bounds.StateUpdateUnbounded;
import de.tum.in.prism.core.builder.AnnotatedModel;
import de.tum.in.prism.core.explorer.Explorer;
import de.tum.in.prism.util.Distribution;
import de.tum.in.prism.util.Sample;
import explicit.DTMC;
import explicit.MDP;
import explicit.MDPSimple;
import explicit.SuccessorsIterator;
import explicit.rewards.MDPRewards;
import explicit.rewards.MDPRewardsSimple;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;
import prism.PrismUtils;

public class Util {
  public static explicit.Distribution scale(Distribution distribution) {
    double total = distribution.sum();
    if (total == 0.0d) {
      return null;
    }
    if (total == 1.0d) {
      return new explicit.Distribution(distribution.objectIterator());
    }
    Map<Integer, Double> map = new HashMap<>(distribution.size());
    for (Map.Entry<Integer, Double> entry : distribution) {
      map.put(entry.getKey(), entry.getValue() / total);
    }
    return new explicit.Distribution(map.entrySet().iterator());
  }

  public static int sampleNextState(List<Distribution> choices, SuccessorHeuristic heuristic,
      IntToDoubleFunction bounds, ToDoubleFunction<Distribution> expectedBounds,
      IntPredicate ignoreStates) {
    if (choices.isEmpty()) {
      return -1;
    }

    if (heuristic == SuccessorHeuristic.GRAPH_WEIGHTED
        || heuristic == SuccessorHeuristic.GRAPH_BOUNDS) {
      Int2DoubleMap map = new Int2DoubleOpenHashMap();

      for (Distribution choice : choices) {
        choice.forEach(entry -> {
          if (ignoreStates.test(entry.getIntKey())) {
            return;
          }
          double value = bounds.applyAsDouble(entry.getIntKey());
          if (heuristic == SuccessorHeuristic.GRAPH_WEIGHTED) {
            value *= entry.getDoubleValue();
          }
          map.merge(entry.getIntKey(), value, Double::max);
        });
      }

      return Sample.sample(map);
    }

    int choiceCount = choices.size();
    Distribution distribution;

    if (choiceCount == 1) {
      distribution = choices.get(0);
    } else {
      int maximalBestActions = 0;
      double bestValue = 0d;
      double[] actionUpperBounds = new double[choiceCount];
      for (int choice = 0; choice < choiceCount; choice++) {
        double upperBound = expectedBounds.applyAsDouble(choices.get(choice));
        actionUpperBounds[choice] = upperBound;
        if (upperBound > bestValue) {
          maximalBestActions = PrismUtils.doublesAreEqual(upperBound, bestValue)
              ? maximalBestActions + 1 : 1;
          bestValue = upperBound;
        } else if (PrismUtils.doublesAreEqual(upperBound, bestValue)) {
          maximalBestActions += 1;
        }
      }

      if (bestValue == 0.0d) {
        // All successors have an upper bound == 0
        return -1;
      }

      // maximalBestActions was an upper bound on the amount, compute precisely now
      int bestActionCount = 0;
      int[] bestActions = new int[maximalBestActions];
      for (int choice = 0; choice < choiceCount; choice++) {
        if (PrismUtils.doublesAreEqual(bestValue, actionUpperBounds[choice])) {
          bestActions[bestActionCount] = choice;
          bestActionCount += 1;
        }
      }

      // There has to be a witness for the bestValue
      assert bestActionCount > 0;
      distribution = choices.get(Sample.sampleUniform(bestActions, bestActionCount));
      assert PrismUtils.doublesAreEqual(bestValue, expectedBounds.applyAsDouble(distribution));
    }

    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      int successor = distribution.getSupport().firstInt();
      return ignoreStates.test(successor) ? -1 : successor;
    }

    // Selected the action, now sample the successor
    NatBitSet support = distribution.getSupport();
    int size = support.size();
    int[] successors = new int[size];
    double[] successorValues = new double[size];

    int index = 0;
    IntIterator iterator = support.iterator();
    while (iterator.hasNext()) {
      int successor = iterator.nextInt();
      if (ignoreStates.test(successor)) {
        continue;
      }

      successors[index] = successor;
      switch (heuristic) {
        case BOUNDS:
          successorValues[index] = bounds.applyAsDouble(successor);
          break;
        case WEIGHTED:
          successorValues[index] = distribution.get(successor) * bounds.applyAsDouble(successor);
          break;
        case PROB:
          successorValues[index] = distribution.get(successor);
          break;
        default:
          throw new AssertionError();
      }
      index += 1;
    }

    int sample = Sample.sample(successorValues, index);
    assert sample == -1 || !ignoreStates.test(successors[sample]);
    return sample == -1 ? -1 : successors[sample];
  }


  public static List<NatBitSet> unionFindPartition(IntIterator iterator, IntUnionFind unionFind) {
    Int2ObjectMap<NatBitSet> unionRootMap = new Int2ObjectAVLTreeMap<>();
    List<NatBitSet> foundBitSets = new ArrayList<>();
    while (iterator.hasNext()) {
      int state = iterator.nextInt();
      unionRootMap.computeIfAbsent(unionFind.find(state), s -> {
        NatBitSet result = NatBitSets.set();
        foundBitSets.add(result);
        return result;
      }).set(state);
    }
    return foundBitSets;
  }

  public static void mdpWithBoundsToDotFile(String filename, AnnotatedModel<MDP> annotatedModel,
      StateUpdateUnbounded bounds, IntPredicate stateFilter) {
    MDP mdp = annotatedModel.model;
    NatBitSet exploredStates = annotatedModel.exploredStates;

    StringBuilder dotString = new StringBuilder("digraph Model {\n\tnode [shape=box];\n");
    for (int state = 0; state < mdp.getNumStates(); state++) {
      if (stateFilter.test(state)) {
        continue;
      }

      dotString.append(state).append(" [style=filled fillcolor=\"");
      boolean appendBounds;
      if (mdp.isInitialState(state)) {
        dotString.append("#9999CC");
        appendBounds = true;
      } else if (!exploredStates.contains(state)) {
        dotString.append("#CC2222");
        appendBounds = false;
      } else if (bounds.isSolved(state)) {
        dotString.append("#B2B48F");
        appendBounds = false;
      } else if (bounds.isZeroDifference(state)) {
        dotString.append("#CD9D87");
        appendBounds = false;
      } else {
        dotString.append("#DDDDDD");
        appendBounds = true;
      }
      dotString.append("\",label=\"").append(state);
      if (appendBounds) {
        dotString.append(" (").append(String.format("%4.4f", bounds.getUpperBound(state)))
            .append(")");
      }
      dotString.append("\"]\n");

      int numChoices = mdp.getNumChoices(state);
      for (int action = 0; action < numChoices; action++) {
        Object actionLabel = mdp.getAction(state, action);
        if (mdp.getNumTransitions(state, action) == 1) {
          SuccessorsIterator iterator = mdp.getSuccessors(state, action);
          int successor = iterator.nextInt();
          assert !iterator.hasNext();

          dotString.append(state).append(" -> ").append(successor);
          if (actionLabel != null) {
            dotString.append("[label=\"").append(actionLabel).append("\"]");
          }
          dotString.append(";\n");
        } else {
          String actionNode = "a" + state + "_" + action;

          dotString.append(state).append(" -> ").append(actionNode)
              .append(" [arrowhead=none,label=\"").append(action);
          if (actionLabel != null) {
            dotString.append(":").append(actionLabel);
          }

          dotString.append("\" ];\n");
          dotString.append(actionNode).append(" [shape=point,height=0.1];\n");

          mdp.forEachTransition(state, action, (source, target, probability) ->
          {
            if (stateFilter.test(target)) {
              return;
            }
            dotString.append(actionNode).append(" -> ").append(target);
            if (!PrismUtils.doublesAreEqual(probability, 1.0d)) {
              dotString.append(" [label=\"").append(String.format("%.3f", probability))
                  .append("\"]");
            }
            dotString.append(";\n");
          });
        }
      }
    }
    dotString.append("}\n");
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename),
        StandardCharsets.UTF_8)) {
      writer.append(dotString.toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void dtmcWithBoundsToDotFile(String filename, AnnotatedModel<DTMC> annotatedModel,
      StateUpdateUnbounded bounds, IntPredicate stateFilter) {
    DTMC dtmc = annotatedModel.model;
    NatBitSet exploredStates = annotatedModel.exploredStates;

    StringBuilder dotString = new StringBuilder("digraph Model {\n\tnode [shape=box];\n");
    for (int state = 0; state < dtmc.getNumStates(); state++) {
      if (stateFilter.test(state)) {
        continue;
      }

      dotString.append(state).append(" [style=filled fillcolor=\"");
      boolean appendBounds;
      if (dtmc.isInitialState(state)) {
        dotString.append("#9999CC");
        appendBounds = true;
      } else if (!exploredStates.contains(state)) {
        dotString.append("#CC2222");
        appendBounds = false;
      } else if (bounds.isSolved(state)) {
        dotString.append("#B2B48F");
        appendBounds = false;
      } else if (bounds.isZeroDifference(state)) {
        dotString.append("#CD9D87");
        appendBounds = false;
      } else {
        dotString.append("#DDDDDD");
        appendBounds = true;
      }
      dotString.append("\",label=\"").append(state);
      if (appendBounds) {
        dotString.append(" (").append(String.format("%4.4f", bounds.getUpperBound(state)))
            .append(")");
      }
      dotString.append("\"]\n");

      if (dtmc.getNumTransitions(state) == 1) {
        SuccessorsIterator iterator = dtmc.getSuccessors(state);
        int successor = iterator.nextInt();
        assert !iterator.hasNext();

        dotString.append(state).append(" -> ").append(successor);
        dotString.append(";\n");
      } else {
        String transitionNode = "a" + state;

        dotString.append(state).append(" -> ").append(transitionNode).append(" [arrowhead=none];\n")
            .append(transitionNode).append(" [shape=point,height=0.1];\n");
        dtmc.forEachTransition(state, (source, target, probability) ->
        {
          if (stateFilter.test(target)) {
            return;
          }
          dotString.append(transitionNode).append(" -> ").append(target);
          if (!PrismUtils.doublesAreEqual(probability, 1.0d)) {
            dotString.append(" [label=\"").append(String.format("%.3f", probability)).append("\"]");
          }
          dotString.append(";\n");
        });
      }
    }
    dotString.append("}\n");
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename),
        StandardCharsets.UTF_8)) {
      writer.append(dotString.toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static MDPRewards buildRewards(Explorer<MDP> explorer, int rewardIndex)
      throws PrismException {
    MDP partialModel = explorer.model();
    ModelGenerator generator = explorer.generator();

    int states = partialModel.getNumStates();
    MDPRewardsSimple rewards = new MDPRewardsSimple(states);

    for (int stateNumber = 0; stateNumber < states; stateNumber++) {
      State state = explorer.getState(stateNumber);
      double stateReward = generator.getStateReward(rewardIndex, state);
      rewards.setStateReward(stateNumber, stateReward);

      int choices = partialModel.getNumChoices(stateNumber);
      for (int action = 0; action < choices; action++) {
        Object actionLabel = partialModel.getAction(stateNumber, action);
        rewards.setTransitionReward(stateNumber, action,
            generator.getStateActionReward(rewardIndex, state, actionLabel));
      }
    }
    return rewards;
  }

  public static RestrictedMdp buildRestrictedModel(MDP mdp, IntSet states) {
    MDPSimple restrictedModel = new MDPSimple();
    int[] originalToRestrictedStates = new int[mdp.getNumStates()];
    Arrays.fill(originalToRestrictedStates, -1);
    states.forEach((int allowedState) -> originalToRestrictedStates[allowedState] = restrictedModel
        .addState());

    int restrictedStates = restrictedModel.getNumStates();
    assert restrictedStates == states.size();
    int[] restrictedToOriginalStates = new int[restrictedStates];
    NatBitSet[] restrictedActions = new NatBitSet[restrictedStates];

    for (int originalState = 0; originalState < mdp.getNumStates(); originalState++) {
      if (originalToRestrictedStates[originalState] == -1) {
        continue;
      }
      int restrictedState = originalToRestrictedStates[originalState];
      restrictedToOriginalStates[restrictedState] = originalState;

      int numChoices = mdp.getNumChoices(originalState);
      BoundedNatBitSet removedActions = NatBitSets.boundedSet(numChoices, 0);
      int addedActions = 0;
      for (int choiceIndex = 0; choiceIndex < numChoices; choiceIndex++) {
        Distribution distribution = new Distribution();
        mdp.forEachTransition(originalState, choiceIndex, (__, t, p) -> {
          int restrictedDestination = originalToRestrictedStates[t];
          if (restrictedDestination >= 0) {
            distribution.add(restrictedDestination, p);
          }
        });
        if (distribution.isEmpty()) {
          removedActions.set(choiceIndex);
          continue;
        }
        explicit.Distribution scaledDistribution = scale(distribution);
        restrictedModel.addActionLabelledChoice(restrictedState, scaledDistribution,
            mdp.getAction(originalState, choiceIndex));
        addedActions += 1;
      }
      restrictedActions[restrictedState] = NatBitSets.compact(removedActions.complement());
      if (addedActions == 0) {
        restrictedModel.addDeadlockState(restrictedState);
      }
    }
    mdp.getInitialStates().forEach(initialState -> {
      int restrictedInitialState = originalToRestrictedStates[initialState];
      if (restrictedInitialState != -1) {
        restrictedModel.addInitialState(restrictedInitialState);
      }
    });

    return new RestrictedMdp(restrictedModel, i -> restrictedToOriginalStates[i],
        i -> restrictedActions[i]);
  }

  public static boolean doublesAreLessOrEqual(double d1, double d2) {
    return d1 <= d2 || PrismUtils.doublesAreEqual(d1, d2);
  }
}
