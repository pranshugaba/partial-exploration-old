package de.tum.in.pet.values.bounded;

import de.tum.in.pet.values.Bounds;

public interface StateValuesBounded extends StateValuesBoundedFunction {
  default void setUpperBound(int state, int remainingSteps, double value) {
    setBounds(state, remainingSteps, lowerBound(state, remainingSteps), value);
  }

  default void setLowerBound(int state, int remainingSteps, double value) {
    setBounds(state, remainingSteps, value, upperBound(state, remainingSteps));
  }

  default void setBounds(int state, int remainingSteps, Bounds bounds) {
    setBounds(state, remainingSteps, bounds.lowerBound(), bounds.upperBound());
  }

  void setBounds(int state, int remainingSteps, double lowerBound, double upperBound);
}
