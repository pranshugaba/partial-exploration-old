package de.tum.in.pet.values;

import de.tum.in.pet.model.Distribution;

@FunctionalInterface
public interface StateValueFunction extends DifferenceFunction,
    LowerBoundsFunction, UpperBoundsFunction {
  Bounds bounds(int state);

  default Bounds bounds(int state, Distribution distribution) {
    return Bounds.of(lowerBound(state, distribution), upperBound(state, distribution));
  }

  @Override
  default double upperBound(int state) {
    return bounds(state).upperBound();
  }

  @Override
  default double lowerBound(int state) {
    return bounds(state).lowerBound();
  }

  @Override
  default double difference(int state) {
    return bounds(state).difference();
  }
}
