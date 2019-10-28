package de.tum.in.pet.values.unbounded;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.util.Util;

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
