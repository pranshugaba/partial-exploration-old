package de.tum.in.pet.util;

import static prism.PrismUtils.doublesAreEqual;

import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.unionfind.IntUnionFind;
import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.model.Action;
import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.model.MDP;
import de.tum.in.pet.model.Model;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.values.StateValueFunction;
import explicit.MDPSimple;
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

public final class Util {
  public static final double DEFAULT_PRECISION = 1e-6;

  private Util() {}

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
      ToDoubleFunction<Distribution> selectionScore, IntToDoubleFunction differences,
      IntPredicate ignoreStates) {
    if (choices.isEmpty()) {
      return -1;
    }

    if (heuristic == SuccessorHeuristic.GRAPH_WEIGHTED
        || heuristic == SuccessorHeuristic.GRAPH_DIFFERENCE) {
      Int2DoubleMap map = new Int2DoubleOpenHashMap();

      for (Distribution choice : choices) {
        choice.forEach(entry -> {
          if (ignoreStates.test(entry.getIntKey())) {
            return;
          }
          double value = differences.applyAsDouble(entry.getIntKey());
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
      double bestValue = Double.NEGATIVE_INFINITY;
      double[] actionUpperBounds = new double[choiceCount];
      for (int choice = 0; choice < choiceCount; choice++) {
        Distribution successors = choices.get(choice);
        if (successors.isEmpty()) {
          continue;
        }
        double upperBound = selectionScore.applyAsDouble(successors);
        actionUpperBounds[choice] = upperBound;
        if (upperBound > bestValue) {
          maximalBestActions = doublesAreEqual(upperBound, bestValue) ? maximalBestActions + 1 : 1;
          bestValue = upperBound;
        } else if (doublesAreEqual(upperBound, bestValue)) {
          maximalBestActions += 1;
        }
      }

      if (bestValue == 0.0d) {
        // All successors have an score == 0
        return -1;
      }

      // maximalBestActions was an upper bound on the amount, compute precisely now
      int bestActionCount = 0;
      int[] bestActions = new int[maximalBestActions];
      for (int choice = 0; choice < choiceCount; choice++) {
        if (doublesAreEqual(bestValue, actionUpperBounds[choice])) {
          bestActions[bestActionCount] = choice;
          bestActionCount += 1;
        }
      }

      // There has to be a witness for the bestValue
      assert bestActionCount > 0;
      distribution = choices.get(Sample.sampleUniform(bestActions, bestActionCount));
      assert doublesAreEqual(bestValue, selectionScore.applyAsDouble(distribution));
    }

    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      int successor = distribution.support().firstInt();
      return ignoreStates.test(successor) ? -1 : successor;
    }

    // Selected the action, now sample the successor
    NatBitSet support = distribution.support();
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
        case DIFFERENCE:
          successorValues[index] = differences.applyAsDouble(successor);
          break;
        case WEIGHTED:
          successorValues[index] = distribution.get(successor)
              * differences.applyAsDouble(successor);
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

  public static void modelWithBoundsToDotFile(String filename,
      Model model, Explorer<?, ?> explorer, StateValueFunction values, IntPredicate stateFilter,
      IntPredicate highlight) {
    modelWithBoundsToDotFile(filename, new AnnotatedModel<>(model, explorer::getState,
        explorer.exploredStates()), values, stateFilter, highlight);
  }

  public static void modelWithBoundsToDotFile(String filename,
      AnnotatedModel<? extends Model> annotatedModel, StateValueFunction values,
      IntPredicate stateFilter, IntPredicate highlight) {
    Model model = annotatedModel.model;
    NatBitSet exploredStates = annotatedModel.exploredStates;

    StringBuilder dotString = new StringBuilder("digraph Model {\n\tnode [shape=box];\n");

    for (int state = 0; state < model.getNumStates(); state++) {
      if (!stateFilter.test(state)) {
        continue;
      }

      dotString.append(state).append(" [style=filled fillcolor=\"");
      boolean appendBounds;
      if (highlight != null && highlight.test(state)) {
        dotString.append("#22CC22");
        appendBounds = true;
      } else if (model.isInitialState(state)) {
        dotString.append("#9999CC");
        appendBounds = true;
      } else if (!exploredStates.contains(state)) {
        dotString.append("#CC2222");
        appendBounds = false;
      } else if (values.isZeroDifference(state)) {
        dotString.append("#CD9D87");
        appendBounds = true;
      } else {
        dotString.append("#DDDDDD");
        appendBounds = true;
      }
      dotString.append("\",label=\"").append(state);
      if (appendBounds) {
        dotString.append(' ').append(values.bounds(state));
      }
      dotString.append("\"];\n");

      int actionIndex = 0;
      for (Action action : model.getActions(state)) {
        Object actionLabel = action.label();
        if (action.distribution().size() == 1) {
          IntIterator iterator = action.distribution().support().iterator();
          int successor = iterator.nextInt();
          assert !iterator.hasNext();

          dotString.append(state).append(" -> ").append(successor);
          if (actionLabel != null) {
            dotString.append("[label=\"").append(actionLabel).append("\"]");
          }
          dotString.append(";\n");
        } else {
          String actionNode = "a" + state + '_' + actionIndex;

          dotString.append(state).append(" -> ").append(actionNode)
              .append(" [arrowhead=none,label=\"").append(actionIndex);
          if (actionLabel != null) {
            dotString.append(':').append(actionLabel);
          }

          dotString.append("\" ];\n");
          dotString.append(actionNode).append(" [shape=point,height=0.1];\n");

          action.distribution().forEach((target, probability) -> {
            /* if (!stateFilter.test(target)) {
              return;
            } */
            dotString.append(actionNode).append(" -> ").append(target);
            if (!doublesAreEqual(probability, 1.0d)) {
              dotString.append(" [label=\"").append(String.format("%.3f", probability))
                  .append("\"]");
            }
            dotString.append(";\n");
          });
        }
        actionIndex += 1;
      }
    }
    dotString.append("}\n");
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename),
        StandardCharsets.UTF_8)) {
      writer.append(dotString.toString());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
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
    mdp.getInitialStates().forEach((int initialState) -> {
      int restrictedInitialState = originalToRestrictedStates[initialState];
      if (restrictedInitialState != -1) {
        restrictedModel.addInitialState(restrictedInitialState);
      }
    });

    // CSOFF: Indentation
    return new RestrictedMdp(restrictedModel, i -> restrictedToOriginalStates[i],
        i -> restrictedActions[i]);
    // CSON: Indentation
  }

  public static boolean doublesAreLessOrEqual(double d1, double d2) {
    return d1 <= d2 || doublesAreEqual(d1, d2);
  }
}
