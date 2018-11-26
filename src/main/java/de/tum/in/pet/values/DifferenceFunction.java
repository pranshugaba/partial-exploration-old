package de.tum.in.pet.values;

import de.tum.in.pet.model.Distribution;

@FunctionalInterface
public interface DifferenceFunction {
  double difference(int state);

  default double difference(int state, Distribution distribution) {
    return distribution.sumWeighted(this::difference);
  }

  default boolean isZeroDifference(int state) {
    return difference(state) == 0.0d;
  }
}
