package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.StateInterpretation;
import de.tum.in.pet.values.StateVerdict;

public class QualitativeVerdict implements StateVerdict, StateInterpretation {
  private final QualitativeQuery type;
  private final double threshold;

  public QualitativeVerdict(QualitativeQuery type, double threshold) {
    this.threshold = threshold;
    this.type = type;
  }

  @Override
  public boolean isSolved(int state, Bounds bounds) {
    switch (type) {
      case GREATER_OR_EQUAL:
        return bounds.lowerBound() >= threshold || bounds.upperBound() < threshold;
      case GREATER_THAN:
        return bounds.lowerBound() > threshold || bounds.upperBound() <= threshold;
      case LESS_OR_EQUAL:
        return bounds.upperBound() <= threshold || bounds.lowerBound() > threshold;
      case LESS_THAN:
        return bounds.upperBound() < threshold || bounds.lowerBound() >= threshold;
      default:
        throw new AssertionError();
    }
  }

  @Override
  public Object interpret(int state, Bounds bounds) {
    assert isSolved(state, bounds);

    switch (type) {
      case GREATER_OR_EQUAL:
        return bounds.lowerBound() >= threshold;
      case GREATER_THAN:
        return bounds.lowerBound() > threshold;
      case LESS_OR_EQUAL:
        return bounds.upperBound() <= threshold;
      case LESS_THAN:
        return bounds.upperBound() < threshold;
      default:
        throw new AssertionError();
    }
  }

  @Override
  public String toString() {
    return type + "/" + threshold;
  }
}
