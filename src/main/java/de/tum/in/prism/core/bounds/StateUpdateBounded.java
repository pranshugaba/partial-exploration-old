package de.tum.in.prism.core.bounds;

import de.tum.in.prism.util.Distribution;

public interface StateUpdateBounded {
  StateBounds getBounds(int state, int remainingSteps);


  default double getUpperBound(int state, int remainingSteps) {
    return getBounds(state, remainingSteps).upperBound();
  }

  default double getUpperBound(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> getUpperBound(s, remainingSteps));
  }

  default double getLowerBound(int state, int remainingSteps) {
    return getBounds(state, remainingSteps).lowerBound();
  }

  default double getLowerBound(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> getLowerBound(s, remainingSteps));
  }

  default double getDifference(int state, int remainingSteps) {
    return getBounds(state, remainingSteps).difference();
  }

  default double getDifference(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> getDifference(s, remainingSteps));
  }


  default boolean isSolved(int state, int remainingSteps) {
    return getBounds(state, remainingSteps).difference() < getPrecision();
  }

  default boolean isZeroUpperBound(int state, int remainingSteps) {
    return getUpperBound(state, remainingSteps) == 0.0d;
  }

  default boolean isOneLowerBound(int state, int remainingSteps) {
    return getLowerBound(state, remainingSteps) == 1.0d;
  }

  default boolean isZeroDifference(int state, int remainingSteps) {
    return getBounds(state, remainingSteps).difference() == 0.0d;
  }


  void setUpperBound(int state, int remainingSteps, double value);

  void setLowerBound(int state, int remainingSteps, double value);

  void setBounds(int state, int remainingSteps, double lowerBound, double upperBound);

  default void setZero(int state, int remainingSteps) {
    setBounds(state, remainingSteps, 0.0d, 0.0d);
  }

  default void setOne(int state, int remainingSteps) {
    setBounds(state, remainingSteps, 1.0d, 1.0d);
  }

  void setZero(int state);

  void setOne(int state);


  void setPrecision(double precision);

  double getPrecision();

  void clear(int state);
}
