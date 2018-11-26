package de.tum.in.pet.values;

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


  default void setZero(int state) {
    setBounds(state, 0.0d, 0.0d);
  }

  default void setOne(int state) {
    setBounds(state, 1.0d, 1.0d);
  }


  void clear(int state);
}
