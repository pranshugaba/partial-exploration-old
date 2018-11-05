package de.tum.in.prism.core.bounds;

import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import java.util.Arrays;
import prism.PrismUtils;

public class StateBoundsDense implements StateBounds {
  private double[] reachabilityUpperBounds = DoubleArrays.EMPTY_ARRAY;
  private double precision = 1.0d;

  public StateBoundsDense() {
    // reachabilityUpperBounds.defaultReturnValue(Double.NaN);
  }

  @Override
  public double getUpperBound(int state) {
    return state < reachabilityUpperBounds.length ? reachabilityUpperBounds[state] : 1.0d;
  }

  @Override
  public boolean isSolved(int state) {
    return state < reachabilityUpperBounds.length && reachabilityUpperBounds[state] < precision;
  }

  @Override
  public boolean isZero(int state) {
    return state < reachabilityUpperBounds.length && reachabilityUpperBounds[state] < precision;
  }

  @Override
  public void setUpperBound(int state, double value) {
    assert 0 <= value && (value <= 1.0d || PrismUtils.doublesAreEqual(value, 1.0d));

    if (reachabilityUpperBounds.length <= state) {
      int oldSize = reachabilityUpperBounds.length;
      reachabilityUpperBounds = Arrays.copyOf(reachabilityUpperBounds, (state + 1) * 2);
      Arrays.fill(reachabilityUpperBounds, oldSize, reachabilityUpperBounds.length, 1.0d);
    }

    double oldValue = reachabilityUpperBounds[state];
    reachabilityUpperBounds[state] = value;
    assert
        Double.isNaN(oldValue) || value <= oldValue || PrismUtils.doublesAreEqual(value, oldValue) :
        value + " " + oldValue;
  }

  @Override
  public void setZero(int state) {
    setUpperBound(state, 0.0d);
    assert getUpperBound(state) == 0.0d;
  }

  @Override
  public void setPrecision(double newPrecision) {
    precision = newPrecision;
  }

  @Override
  public void drop(int state) {
    reachabilityUpperBounds[state] = 1.0d;
    assert getUpperBound(state) == 1.0d;
  }
}
