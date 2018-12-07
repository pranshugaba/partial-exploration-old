package de.tum.in.pet.implementation.core;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.ValueVerdict;
import de.tum.in.pet.values.bounded.StateUpdateBounded;
import de.tum.in.pet.values.bounded.StateValuesBoundedFunction;
import java.util.List;

public class StateUpdateBoundedCore implements StateUpdateBounded, ValueVerdict {
  private final double precision;

  public StateUpdateBoundedCore(double precision) {
    this.precision = precision;
  }

  @Override
  public boolean isSolved(Bounds bounds) {
    return bounds.upperBound() < precision;
  }

  @Override
  public Bounds update(int state, int remainingSteps, List<Distribution> choices,
      StateValuesBoundedFunction values) {
    if (choices.isEmpty()) {
      return Bounds.ZERO_ZERO;
    }

    double maximalValue = 0.0d;
    for (Distribution distribution : choices) {
      double expectedValue = values.upperBound(state, remainingSteps, distribution);
      if (expectedValue > maximalValue) {
        maximalValue = expectedValue;
      }
    }

    return Bounds.of(0.0d, maximalValue);
  }
}
