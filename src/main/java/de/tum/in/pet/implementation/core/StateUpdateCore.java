package de.tum.in.pet.implementation.core;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.InitialValues;
import de.tum.in.pet.values.StateUpdate;
import de.tum.in.pet.values.StateValueFunction;
import de.tum.in.pet.values.StateVerdict;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.List;

public class StateUpdateCore<S> implements StateUpdate, InitialValues<S>, StateVerdict {
  private final double precision;

  public StateUpdateCore(double precision) {
    this.precision = precision;
  }

  @Override
  public Bounds update(int state, List<Distribution> choices, StateValueFunction values) {
    if (choices.isEmpty()) {
      return Bounds.ZERO_ZERO;
    }

    double maximalValue = 0d;
    for (Distribution distribution : choices) {
      double expectedValue = values.upperBound(state, distribution);
      if (expectedValue > maximalValue) {
        maximalValue = expectedValue;
      }
    }

    return Bounds.of(0.0d, maximalValue);
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

  @Override
  public Bounds initialValues(S state) {
    return Bounds.ZERO_ONE;
  }
}
