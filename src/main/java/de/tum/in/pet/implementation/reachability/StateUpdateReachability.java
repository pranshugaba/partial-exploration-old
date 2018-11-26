package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.InitialValues;
import de.tum.in.pet.values.StateUpdate;
import de.tum.in.pet.values.StateValueFunction;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.List;
import java.util.function.IntFunction;
import parser.State;
import prism.PrismException;

public class StateUpdateReachability implements StateUpdate, InitialValues {
  private final IntFunction<State> numberToStateFunction;
  private final TargetPredicate target;
  private final ValueUpdateType update;

  public StateUpdateReachability(IntFunction<State> numberToStateFunction, TargetPredicate target,
      ValueUpdateType update) {
    this.numberToStateFunction = numberToStateFunction;
    this.target = target;
    this.update = update;
  }

  @Override
  public Bounds update(int state, List<Distribution> choices, StateValueFunction values) {
    assert update != ValueUpdateType.UNIQUE_VALUE || choices.size() <= 1;

    if (choices.isEmpty()) {
      return Bounds.ZERO_ZERO;
    }
    if (values.lowerBound(state) == 1.0d) {
      return Bounds.ONE_ONE;
    }
    if (values.upperBound(state) == 0.0d) {
      return Bounds.ZERO_ZERO;
    }

    if (choices.size() == 1) {
      return values.bounds(state, choices.get(0));
    }

    double newLowerBound;
    double newUpperBound;
    if (update == ValueUpdateType.MAX_VALUE) {
      newLowerBound = 0.0d;
      newUpperBound = 0.0d;
      for (Distribution distribution : choices) {
        double upperBound = values.upperBound(state, distribution);
        if (upperBound > newUpperBound) {
          newUpperBound = upperBound;
        }
        double lowerBound = values.lowerBound(state, distribution);
        if (lowerBound > newLowerBound) {
          newLowerBound = lowerBound;
        }
      }
    } else {
      assert update == ValueUpdateType.MIN_VALUE;

      newUpperBound = 1.0d;
      newLowerBound = 1.0d;
      for (Distribution distribution : choices) {
        double upperBound = values.upperBound(state, distribution);
        if (upperBound < newUpperBound) {
          newUpperBound = upperBound;
        }
        double lowerBound = values.lowerBound(state, distribution);
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
      IntCollection collapsedStates, StateValueFunction values) throws PrismException {
    assert update != ValueUpdateType.MIN_VALUE || choices.isEmpty();

    // TODO This is wrong in general for MIN.
    IntIterator iterator = collapsedStates.iterator();
    while (iterator.hasNext()) {
      int next = iterator.nextInt();
      if (target.isTargetState(numberToStateFunction.apply(next))) {
        return Bounds.ONE_ONE;
      }
    }
    return update(state, choices, values);
  }

  @Override
  public boolean isSmallestFixPoint() {
    return update == ValueUpdateType.MIN_VALUE;
  }

  @Override
  public Bounds initialValues(State state) throws PrismException {
    return target.isTargetState(state) ? Bounds.ONE_ONE : Bounds.ZERO_ONE;
  }
}
