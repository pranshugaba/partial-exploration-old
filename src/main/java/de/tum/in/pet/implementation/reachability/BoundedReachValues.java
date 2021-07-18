package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;
import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;

import de.tum.in.pet.sampler.BoundedValues;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;

public class BoundedReachValues implements BoundedValues {
  private final double precision;
  private final SuccessorHeuristic heuristic;
  private final Int2ObjectMap<Bounds[]> bounds = new Int2ObjectOpenHashMap<>();
  private final IntPredicate target;
  private final ValueUpdate update;

  public BoundedReachValues(double precision, SuccessorHeuristic heuristic, IntPredicate target,
      ValueUpdate update) {
    this.precision = precision;
    this.heuristic = heuristic;
    this.target = target;
    this.update = update;
  }

  @Override
  public Bounds bounds(int state, int remaining) {
    assert 0 <= remaining;

    if (target.test(state)) {
      return Bounds.reachOne();
    }
    if (remaining == 0) {
      return Bounds.reachZero();
    }

    Bounds[] values = bounds.get(state);
    return values == null || remaining >= values.length
        ?  Bounds.reachUnknown()
        : values[remaining];
  }

  private Bounds successorBounds(int remaining, Distribution distribution) {
    double lower = 0.0d;
    double upper = 0.0d;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      Bounds successorBounds = bounds(successor, remaining - 1);
      double probability = entry.getDoubleValue();
      lower += successorBounds.lowerBound() * probability;
      upper += successorBounds.upperBound() * probability;
    }
    return Bounds.reach(lower, upper);
  }

  @Override
  public boolean isSolved(int state, int remaining) {
    return remaining == 0 || bounds(state, remaining).difference() < precision;
  }

  @Override
  public int sampleNextState(int state, int remaining, List<Distribution> choices) {
    ToDoubleFunction<Integer> actionScore = i ->
        choices.get(i).sumWeighted(s -> bounds(s, remaining - 1).upperBound());
    IntToDoubleFunction successorDifferences = s -> bounds(s, remaining - 1).difference();

    return SampleUtil.sampleNextState(choices, heuristic, actionScore, successorDifferences);
  }

  public void setBounds(int state, int remaining, Bounds bounds) {
    update(state, remaining, bounds);
  }

  @Override
  public void update(int state, int remaining, List<Distribution> choices) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    Bounds oldBounds = bounds(state, remaining);
    if (isOne(oldBounds.lowerBound()) || isZero(oldBounds.upperBound())) {
      return;
    }
    assert !target.test(state);

    Bounds newBounds;
    if (choices.isEmpty()) {
      newBounds = Bounds.reachZero();
      setBounds(state, remaining, newBounds);
    } else if (choices.size() == 1) {
      newBounds = successorBounds(state, choices.get(0));
      setBounds(state, remaining, newBounds);
    } else {
      double newLowerBound;
      double newUpperBound;
      if (update == ValueUpdate.MAX_VALUE) {
        newLowerBound = 0.0d;
        newUpperBound = 0.0d;
        for (Distribution distribution : choices) {
          Bounds bounds = successorBounds(remaining, distribution);
          double upperBound = bounds.upperBound();
          if (upperBound > newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound > newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      } else {
        assert update == ValueUpdate.MIN_VALUE;

        newUpperBound = 1.0d;
        newLowerBound = 1.0d;
        for (Distribution distribution : choices) {
          Bounds bounds = successorBounds(remaining, distribution);
          double upperBound = bounds.upperBound();
          if (upperBound < newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound < newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      }
      assert newLowerBound <= newUpperBound;
      newBounds = Bounds.of(newLowerBound, newUpperBound);
      setBounds(state, remaining, newBounds);
    }
  }

  @Override
  public void update(int state, int remaining, Bounds bounds) {
    checkArgument(0 <= remaining);

    Bounds[] values = this.bounds.get(state);

    if (values == null) {
      Bounds[] newValues = new Bounds[remaining + 1];
      Arrays.fill(newValues, 0, remaining + 1, bounds);
      this.bounds.put(state, newValues);
    } else if (values.length <= remaining) {
      int oldLength = values.length;
      int newLength = Math.max(oldLength * 2, remaining + 1);
      values = Arrays.copyOf(values, newLength);
      Arrays.fill(values, oldLength, remaining + 1, bounds);
      Arrays.fill(values, remaining + 1, newLength, Bounds.reachUnknown());
      this.bounds.put(state, values);
    } else {
      values[remaining] = bounds;
    }
  }

  @Override
  public void explored(int state, int remaining) {
    // empty
  }

  @Override
  public boolean storesExact() {
    return true;
  }

  @Override
  public boolean stores(int remaining) {
    return true;
  }
}
