package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;
import static de.tum.in.pet.util.Util.isOne;
import static de.tum.in.pet.util.Util.isZero;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.unbounded.StateUpdate;
import de.tum.in.pet.values.unbounded.StateValueFunction;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.List;
import java.util.function.IntPredicate;

public class StateUpdateReachability implements StateUpdate {
  private final IntPredicate target;
  private final ValueUpdate update;

  public StateUpdateReachability(IntPredicate target, ValueUpdate update) {
    this.target = target;
    this.update = update;
  }

  @Override
  public Bounds update(int state, List<Distribution> choices, StateValueFunction values) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    if (isOne(values.lowerBound(state))) {
      assert isOne(values.upperBound(state));
      return Bounds.reachOne();
    }
    if (isZero(values.upperBound(state))) {
      assert isZero(values.lowerBound(state));
      return Bounds.reachZero();
    }
    assert !target.test(state);

    if (choices.isEmpty()) {
      return Bounds.reachZero();
    }
    if (choices.size() == 1) {
      return values.bounds(state, choices.get(0));
    }

    double newLowerBound;
    double newUpperBound;
    if (update == ValueUpdate.MAX_VALUE) {
      newLowerBound = 0.0d;
      newUpperBound = 0.0d;
      for (Distribution distribution : choices) {
        Bounds bounds = values.bounds(state, distribution);
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
        Bounds bounds = values.bounds(state, distribution);
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

  @Override
  public Bounds updateCollapsed(int state, List<Distribution> choices,
      IntCollection collapsedStates, StateValueFunction values) {
    if (update == ValueUpdate.MIN_VALUE) {
      checkArgument(choices.isEmpty());
    }

    IntIterator iterator = collapsedStates.iterator();
    while (iterator.hasNext()) {
      int next = iterator.nextInt();
      if (target.test(next)) {
        return Bounds.reachOne();
      }
    }
    return update(state, choices, values);
  }

  @Override
  public boolean isSmallestFixPoint() {
    return update == ValueUpdate.MIN_VALUE;
  }
}
