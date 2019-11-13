package de.tum.in.pet.util;

import static de.tum.in.probmodels.util.Util.isEqual;
import static de.tum.in.probmodels.util.Util.isZero;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.util.Sample;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;

public final class SampleUtil {
  private SampleUtil() {
  }

  public static int sampleNextState(List<Distribution> choices, SuccessorHeuristic heuristic,
      ToDoubleFunction<Distribution> selectionScore, IntToDoubleFunction successorDifferences,
      IntPredicate ignoreSuccessors) {
    if (choices.isEmpty()) {
      return -1;
    }

    if (heuristic == SuccessorHeuristic.GRAPH_WEIGHTED
        || heuristic == SuccessorHeuristic.GRAPH_DIFFERENCE) {
      Int2DoubleMap map = new Int2DoubleOpenHashMap();

      for (Distribution choice : choices) {
        choice.forEach(entry -> {
          if (ignoreSuccessors.test(entry.getIntKey())) {
            return;
          }
          double value = successorDifferences.applyAsDouble(entry.getIntKey());
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
          maximalBestActions = isEqual(upperBound, bestValue) ? maximalBestActions + 1 : 1;
          bestValue = upperBound;
        } else if (isEqual(upperBound, bestValue)) {
          maximalBestActions += 1;
        }
      }

      if (isZero(bestValue)) {
        // All successors have an score == 0
        return -1;
      }

      // maximalBestActions was an upper bound on the amount, compute precisely now
      int bestActionCount = 0;
      int[] bestActions = new int[maximalBestActions];
      for (int choice = 0; choice < choiceCount; choice++) {
        if (isEqual(bestValue, actionUpperBounds[choice])) {
          bestActions[bestActionCount] = choice;
          bestActionCount += 1;
        }
      }

      // There has to be a witness for the bestValue
      assert bestActionCount > 0;
      distribution = choices.get(Sample.sampleUniform(bestActions, bestActionCount));
      assert isEqual(bestValue, selectionScore.applyAsDouble(distribution));
    }

    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      int successor = distribution.support().firstInt();
      return ignoreSuccessors.test(successor) ? -1 : successor;
    }

    // Selected the action, now sample the successor
    NatBitSet support = distribution.support();
    int size = support.size();
    int[] successors = new int[size];
    double[] successorValues = new double[size];

    int index = 0;
    IntIterator iterator = support.iterator();
    while (iterator.hasNext()) {
      assert index < size;

      int successor = iterator.nextInt();
      if (ignoreSuccessors.test(successor)) {
        continue;
      }

      successors[index] = successor;
      switch (heuristic) {
        case DIFFERENCE:
          successorValues[index] = successorDifferences.applyAsDouble(successor);
          break;
        case WEIGHTED:
          successorValues[index] = distribution.get(successor)
              * successorDifferences.applyAsDouble(successor);
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
    assert sample == -1 || !ignoreSuccessors.test(successors[sample]);
    return sample == -1 ? -1 : successors[sample];
  }
}
