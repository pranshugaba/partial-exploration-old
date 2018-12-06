package de.tum.in.pet.values.unbounded;

import de.tum.in.pet.values.Bounds;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.function.IntConsumer;

public interface StateValues extends StateValueFunction {
  default void setUpperBound(int state, double value) {
    setBounds(state, lowerBound(state), value);
  }

  default void setLowerBound(int state, double value) {
    setBounds(state, value, upperBound(state));
  }

  default void setBounds(int state, Bounds bounds) {
    setBounds(state, bounds.lowerBound(), bounds.upperBound());
  }

  void setBounds(int state, double lowerBound, double upperBound);

  void clear(int state);

  default void clear(IntCollection states) {
    states.forEach((IntConsumer) this::clear);
  }
}
