package de.tum.in.pet.values.bounded;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;

@FunctionalInterface
public interface StateValueBoundedFunction extends DifferenceBoundedFunction,
    LowerBoundsBoundedFunction, UpperBoundsBoundedFunction {
  Bounds bounds(int state, int remainingSteps);

  default Bounds bounds(int state, int remainingSteps, Distribution distribution) {
    double lowerBound = lowerBound(state, remainingSteps, distribution);
    double upperBound = upperBound(state, remainingSteps, distribution);
    return Bounds.of(lowerBound, upperBound);
  }

  @Override
  default double upperBound(int state, int remainingSteps) {
    return bounds(state, remainingSteps).upperBound();
  }

  @Override
  default double lowerBound(int state, int remainingSteps) {
    return bounds(state, remainingSteps).lowerBound();
  }

  @Override
  default double difference(int state, int remainingSteps) {
    return bounds(state, remainingSteps).difference();
  }
}
