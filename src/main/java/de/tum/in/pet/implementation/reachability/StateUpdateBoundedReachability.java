package de.tum.in.pet.implementation.reachability;

import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.bounded.StateUpdateBounded;
import de.tum.in.pet.values.bounded.StateValuesBoundedFunction;
import de.tum.in.probmodels.model.Distribution;
import java.util.List;
import java.util.function.IntPredicate;

public class StateUpdateBoundedReachability implements StateUpdateBounded {
  private final IntPredicate target;
  private final ValueUpdate update;

  public StateUpdateBoundedReachability(IntPredicate target, ValueUpdate update) {
    this.target = target;
    this.update = update;
  }

  @Override
  public Bounds update(int state, int remainingSteps, List<Distribution> choices,
      StateValuesBoundedFunction values) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    if (isOne(values.lowerBound(state, remainingSteps))) {
      assert isOne(values.upperBound(state, remainingSteps));
      return Bounds.reachOne();
    }
    if (isZero(values.upperBound(state, remainingSteps))) {
      assert isZero(values.lowerBound(state, remainingSteps));
      return Bounds.reachZero();
    }
    assert !target.test(state);

    if (choices.isEmpty()) {
      return Bounds.reachZero();
    }

    if (choices.size() == 1) {
      return values.bounds(state, remainingSteps, choices.get(0));
    }

    double newLowerBound;
    double newUpperBound;
    if (update == ValueUpdate.MAX_VALUE) {
      newLowerBound = 0.0d;
      newUpperBound = 0.0d;
      for (Distribution distribution : choices) {
        Bounds bounds = values.bounds(state, remainingSteps, distribution);
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
        Bounds bounds = values.bounds(state, remainingSteps, distribution);
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
    return Bounds.of(newLowerBound, newUpperBound);
  }
}
