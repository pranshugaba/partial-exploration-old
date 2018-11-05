package de.tum.in.prism.core.util;

import com.google.common.collect.Sets;
import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.unionfind.IntUnionFind;
import de.tum.in.prism.core.bounds.StateUpdate;
import de.tum.in.prism.core.builder.AnnotatedModel;
import de.tum.in.prism.core.explorer.Explorer;
import explicit.DTMC;
import explicit.Distribution;
import explicit.MDP;
import explicit.MDPSimple;
import explicit.SuccessorsIterator;
import explicit.rewards.MDPRewards;
import explicit.rewards.MDPRewardsSimple;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;
import prism.PrismUtils;

public class Util {
  private static final Random random = new Random();

  public static double sumWeighted(Distribution distribution, IntToDoubleFunction function) {
    double result = 0.0d;
    for (Map.Entry<Integer, Double> entry : distribution) {
      result += entry.getValue() * function.applyAsDouble(entry.getKey());
    }
    return result;
  }

  public static Distribution scale(Distribution distribution) {
    double total = 0.0d;
    for (Map.Entry<Integer, Double> entry : distribution) {
      total += entry.getValue();
    }
    if (total == 0.0d) {
      return null;
    }
    if (total == 1.0d) {
      return distribution;
    }
    Map<Integer, Double> map = new HashMap<>(distribution.size());
    for (Map.Entry<Integer, Double> entry : distribution) {
      map.put(entry.getKey(), entry.getValue() / total);
    }
    return new Distribution(map.entrySet().iterator());
  }

  public static Distribution map(Distribution distribution, IntUnaryOperator function) {
    Map<Integer, Double> map = new HashMap<>(distribution.size());
    for (Map.Entry<Integer, Double> entry : distribution) {
      int newKey = function.applyAsInt(entry.getKey());
      map.merge(newKey, entry.getValue(), Double::sum);
    }
    System.out.println(distribution);
    System.out.println(map);
    return new Distribution(map.entrySet().iterator());
  }

  public static boolean containsOneOf(Distribution distribution, Set<Integer> set) {
    return !Sets.intersection(distribution.getSupport(), set).isEmpty();
  }

  public static int sample(Distribution distribution) {
    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      return distribution.iterator().next().getKey();
    }

    int[] keys = new int[distribution.size()];
    double[] values = new double[distribution.size()];

    int index = 0;
    for (Map.Entry<Integer, Double> entry : distribution) {
      keys[index] = entry.getKey();
      values[index] = entry.getValue();
      index += 1;
    }

    int sample = sample(values);
    return sample == -1 ? -1 : keys[sample];
  }

  public static int sample(double[] values) {
    double sum = 0.0d;
    for (double v : values) {
      sum += v;
    }

    if (sum == 0.0d) {
      return -1;
    }

    // Sample a random value in [0, sum)
    double sampledValue = random.nextDouble() * sum;
    // Search the successor corresponding to this value
    double partialSum = 0d;
    for (int i = 0; i < values.length; i++) {
      partialSum += values[i];
      if (partialSum >= sampledValue) {
        return i;
      }
    }

    throw new AssertionError("Not sampling any value");
  }

  public static int sample(Int2DoubleMap distribution) {
    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      return distribution.keySet().iterator().nextInt();
    }

    int[] keys = new int[distribution.size()];
    double[] values = new double[distribution.size()];

    int index = 0;
    for (Int2DoubleMap.Entry entry : distribution.int2DoubleEntrySet()) {
      keys[index] = entry.getIntKey();
      values[index] = entry.getDoubleValue();
      index += 1;
    }
    int sample = sample(values);
    return sample == -1 ? -1 : keys[sample];
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

  public static int sampleUniform(IntList values) {
    int size = values.size();
    if (size == 0) {
      return -1;
    }
    if (size == 1) {
      return values.getInt(0);
    }
    return values.getInt(random.nextInt(size));
  }

  public static int sampleUniform(int[] values, int max) {
    assert max <= values.length;
    if (max == 0) {
      return -1;
    }
    if (max == 1) {
      return values[0];
    }
    return values[random.nextInt(max)];
  }

  public static <T> T sampleUniform(List<? extends T> values) {
    int size = values.size();
    if (size == 0) {
      return null;
    }
    return size == 1 ? values.get(0) : values.get(random.nextInt(size));
  }

  public static void mdpWithBoundsToDotFile(String filename, AnnotatedModel<MDP> annotatedModel,
      StateUpdate bounds, IntPredicate stateFilter) {
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
    try (FileWriter fileWriter = new FileWriter(filename)) {
      fileWriter.append(dotString.toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void dtmcWithBoundsToDotFile(String filename, AnnotatedModel<DTMC> annotatedModel,
      StateUpdate bounds, IntPredicate stateFilter) {
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
    try (FileWriter fileWriter = new FileWriter(filename)) {
      fileWriter.append(dotString.toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static MDPRewards buildRewards(Explorer<MDP> explorer, int rewardIndex)
      throws PrismException {
    MDP partialModel = explorer.getModel();
    ModelGenerator generator = explorer.getGenerator();

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
        Distribution scaledDistribution = scale(distribution);
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
