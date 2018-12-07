package de.tum.in.pet.values.bounded;

import de.tum.in.pet.model.Distribution;

@FunctionalInterface
public interface LowerBoundsBoundedFunction {
  double lowerBound(int state, int remainingSteps);

  default double lowerBound(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> lowerBound(s, remainingSteps - 1));
  }

  default boolean isOneLowerBound(int state, int remainingSteps) {
    return lowerBound(state, remainingSteps) == 1.0d;
  }
}
