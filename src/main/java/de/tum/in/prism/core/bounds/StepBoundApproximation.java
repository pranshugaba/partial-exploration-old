package de.tum.in.prism.core.bounds;

import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import it.unimi.dsi.fastutil.ints.IntSet;

public interface StepBoundApproximation {
  double getUpperBound(int state, int remainingSteps);

  void setZero(int state);

  void setZero(int state, int remainingSteps);

  boolean isZero(int state, int remainingSteps);

  void setUpperBound(int state, int remainingSteps, double value);

  void setUpperBounds(IntSet states, Int2DoubleFunction values, int remainingSteps);
}
