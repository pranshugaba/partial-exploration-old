package de.tum.in.prism.core.bounds;

import com.google.common.collect.ImmutableList;
import de.tum.in.prism.core.util.Util;
import explicit.Distribution;
import java.util.List;

public interface StateUpdate {
  double getDifference(int state);

  default double getExpectedDifference(Distribution distribution) {
    return Util.sumWeighted(distribution, this::getDifference);
  }

  double getUpperBound(int state);

  default double getExpectedUpperBound(Distribution distribution) {
    return Util.sumWeighted(distribution, this::getUpperBound);
  }

  double update(int state, List<Distribution> choices);

  boolean isSolved(int state);

  boolean isZeroUpperBound(int state);

  boolean isZeroDifference(int state);

  void setPrecision(double precision);

  void clear(int state);

  default double update(int state, Distribution distribution) {
    return update(state, ImmutableList.of(distribution));
  }
}
