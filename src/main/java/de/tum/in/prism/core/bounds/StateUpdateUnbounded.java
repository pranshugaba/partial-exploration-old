package de.tum.in.prism.core.bounds;

import de.tum.in.prism.util.Distribution;

public interface StateUpdateUnbounded {
  double getPrecision();

  void setPrecision(double precision);


  StateBounds getBounds(int state);


  default double getUpperBound(int state) {
    return getBounds(state).upperBound();
  }

  default double getUpperBound(int state, Distribution distribution) {
    return distribution.sumWeighted(this::getUpperBound);
  }

  default double getLowerBound(int state) {
    return getBounds(state).lowerBound();
  }

  default double getLowerBound(int state, Distribution distribution) {
    return distribution.sumWeighted(this::getLowerBound);
  }

  default double getDifference(int state) {
    return getBounds(state).difference();
  }

  default double getDifference(int state, Distribution distribution) {
    return distribution.sumWeighted(this::getDifference);
  }


  default boolean isSolved(int state) {
    return getBounds(state).difference() < getPrecision();
  }

  default boolean isZeroUpperBound(int state) {
    return getUpperBound(state) == 0.0d;
  }

  default boolean isOneLowerBound(int state) {
    return getLowerBound(state) == 1.0d;
  }

  default boolean isZeroDifference(int state) {
    return getBounds(state).difference() == 0.0d;
  }


  default void setUpperBound(int state, double value) {
    setBounds(state, getLowerBound(state), value);
  }

  default void setLowerBound(int state, double value) {
    setBounds(state, value, getUpperBound(state));
  }

  void setBounds(int state, double lowerBound, double upperBound);


  default void setZero(int state) {
    setBounds(state, 0.0d, 0.0d);
  }

  default void setOne(int state) {
    setBounds(state, 1.0d, 1.0d);
  }


  void clear(int state);
}
