package de.tum.in.pet.values.bounded;

import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.util.Util;

@FunctionalInterface
public interface LowerBoundsBoundedFunction {
  double lowerBound(int state, int remainingSteps);

  default double lowerBound(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> lowerBound(s, remainingSteps - 1));
  }

  default boolean isOneLowerBound(int state, int remainingSteps) {
    return Util.isOne(lowerBound(state, remainingSteps));
  }
}
