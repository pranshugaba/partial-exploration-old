package de.tum.in.pet.values.unbounded;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.util.Util;

@FunctionalInterface
public interface LowerBoundsFunction {
  double lowerBound(int state);

  default double lowerBound(int state, Distribution distribution) {
    return distribution.sumWeighted(this::lowerBound);
  }

  default boolean isOneLowerBound(int state) {
    return Util.isOne(lowerBound(state));
  }
}
