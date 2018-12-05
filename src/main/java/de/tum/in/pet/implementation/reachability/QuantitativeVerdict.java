package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.StateInterpretation;
import de.tum.in.pet.values.StateVerdict;

public class QuantitativeVerdict implements StateVerdict, StateInterpretation<Double> {
  private final double precision;
  private final boolean relativeError;

  public QuantitativeVerdict(double precision, boolean relativeError) {
    this.precision = precision;
    this.relativeError = relativeError;
  }

  @Override
  public boolean isSolved(Bounds bounds) {
    if (relativeError) {
      return bounds.upperBound() <= bounds.lowerBound() * (1 + precision)
          && bounds.lowerBound() >= bounds.upperBound() * (1 - precision);
    }
    return bounds.difference() < precision;
  }

  @Override
  public Double interpret(Bounds bounds) {
    assert isSolved(bounds);

    return bounds.average();
  }

  @Override
  public String toString() {
    return precision + (relativeError ? "(rel)" : "(abs)");
  }
}
