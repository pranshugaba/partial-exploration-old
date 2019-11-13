package de.tum.in.pet.implementation.core;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.ValueVerdict;
import de.tum.in.pet.values.unbounded.StateUpdate;
import de.tum.in.pet.values.unbounded.StateValueFunction;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.List;

public class StateUpdateCore implements StateUpdate, ValueVerdict {
  private final double precision;

  public StateUpdateCore(double precision) {
    this.precision = precision;
  }

  @Override
  public Bounds update(int state, List<Distribution> choices, StateValueFunction values) {
    if (choices.isEmpty()) {
      return Bounds.reachZero();
    }

    double maximalValue = 0d;
    for (Distribution distribution : choices) {
      double expectedValue = values.upperBound(state, distribution);
      if (expectedValue > maximalValue) {
        maximalValue = expectedValue;
      }
    }

    return Bounds.reach(0.0d, maximalValue);
  }

  @Override
  public Bounds updateCollapsed(int state, List<Distribution> choices,
      IntCollection collapsedStates, StateValueFunction values) {
    return update(state, choices, values);
  }

  @Override
  public boolean isSmallestFixPoint() {
    return false;
  }

  @Override
  public boolean isSolved(Bounds bounds) {
    return bounds.upperBound() < precision;
  }
}
