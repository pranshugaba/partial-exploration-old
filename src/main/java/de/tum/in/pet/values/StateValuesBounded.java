package de.tum.in.pet.values;

public interface StateValuesBounded {
  StateValues stepValues(int remainingSteps);


  void setZero(int state);

  void setOne(int state);


  void clear(int state);
}
