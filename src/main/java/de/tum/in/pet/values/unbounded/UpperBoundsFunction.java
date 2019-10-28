package de.tum.in.pet.values.unbounded;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.util.Util;

@FunctionalInterface
public interface UpperBoundsFunction {
  double upperBound(int state);

  default double upperBound(int state, Distribution distribution) {
    return distribution.sumWeighted(this::upperBound);
  }

  default boolean isZeroUpperBound(int state) {
    return Util.isZero(upperBound(state));
  }
}
