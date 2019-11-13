package de.tum.in.pet.values.unbounded;

import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.util.Util;

@FunctionalInterface
public interface DifferenceFunction {
  double difference(int state);

  default double difference(int state, Distribution distribution) {
    return distribution.sumWeighted(this::difference);
  }

  default boolean isZeroDifference(int state) {
    return Util.isZero(difference(state));
  }
}
