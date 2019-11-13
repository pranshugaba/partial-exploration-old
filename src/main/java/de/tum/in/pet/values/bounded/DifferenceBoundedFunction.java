package de.tum.in.pet.values.bounded;

import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.util.Util;

@FunctionalInterface
public interface DifferenceBoundedFunction {
  double difference(int state, int remainingSteps);

  default double difference(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> difference(s, remainingSteps - 1));
  }

  default boolean isZeroDifference(int state, int remainingSteps) {
    return Util.isZero(difference(state, remainingSteps));
  }
}
