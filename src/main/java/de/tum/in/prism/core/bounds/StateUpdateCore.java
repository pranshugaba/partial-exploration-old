package de.tum.in.prism.core.bounds;

import explicit.Distribution;
import java.util.List;

public class StateUpdateCore implements StateUpdate {
  private final StateBounds bounds;

  public StateUpdateCore(StateBounds bounds) {
    this.bounds = bounds;
  }

  @Override
  public double getDifference(int state) {
    return bounds.getUpperBound(state);
  }

  @Override
  public double getExpectedDifference(Distribution distribution) {
    return bounds.getExpectedUpperBound(distribution);
  }

  @Override
  public double getUpperBound(int state) {
    return bounds.getUpperBound(state);
  }

  @Override
  public double getExpectedUpperBound(Distribution distribution) {
    return bounds.getExpectedUpperBound(distribution);
  }

  @Override
  public double update(int state, List<Distribution> choices) {
    if (choices.isEmpty()) {
      bounds.setZero(state);
      return 0d;
    }
    if (choices.size() == 1) {
      return update(state, choices.get(0));
    }

    double maximalValue = 0d;
    for (Distribution distribution : choices) {
      double expectedValue = bounds.getExpectedUpperBound(distribution);
      if (expectedValue > maximalValue) {
        maximalValue = expectedValue;
      }
    }
    bounds.setUpperBound(state, maximalValue);
    return maximalValue;
  }

  @Override
  public boolean isSolved(int state) {
    return bounds.isSolved(state);
  }

  @Override
  public boolean isZeroUpperBound(int state) {
    return bounds.isZero(state);
  }

  @Override
  public boolean isZeroDifference(int state) {
    return bounds.isZero(state);
  }

  @Override
  public void setPrecision(double precision) {
    bounds.setPrecision(precision);
  }

  @Override
  public void clear(int state) {
    bounds.drop(state);
  }

  @Override
  public double update(int state, Distribution distribution) {
    double newBound = bounds.getExpectedUpperBound(distribution);
    bounds.setUpperBound(state, newBound);
    return newBound;
  }
}
