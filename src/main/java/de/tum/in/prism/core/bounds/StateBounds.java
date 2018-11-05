package de.tum.in.prism.core.bounds;

import de.tum.in.prism.core.util.Util;
import explicit.Distribution;

public interface StateBounds {
  double getUpperBound(int state);

  default double getExpectedUpperBound(Distribution distribution) {
    return Util.sumWeighted(distribution, this::getUpperBound);
  }

  boolean isSolved(int state);

  boolean isZero(int state);

  void setUpperBound(int state, double value);

  void setZero(int state);

  void setPrecision(double newPrecision);

  void drop(int state);
}
