package de.tum.in.pet.values;

public class StateValuesFixed implements StateValues {
  private final Bounds bounds;

  public StateValuesFixed(Bounds bounds) {
    this.bounds = bounds;
  }

  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear(int state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bounds bounds(int state) {
    return bounds;
  }
}
