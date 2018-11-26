package de.tum.in.pet.values;

import de.tum.in.pet.model.Distribution;

@FunctionalInterface
public interface UpperBoundsFunction {
  double upperBound(int state);

  default double upperBound(int state, Distribution distribution) {
    return distribution.sumWeighted(this::upperBound);
  }

  default boolean isZeroUpperBound(int state) {
    return upperBound(state) == 0.0d;
  }
}
