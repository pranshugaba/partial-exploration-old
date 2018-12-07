package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.ValueInterpretation;
import de.tum.in.pet.values.ValueVerdict;

public class QualitativeVerdict implements ValueVerdict, ValueInterpretation<Boolean> {
  private final QualitativeQuery type;
  private final double threshold;

  public QualitativeVerdict(QualitativeQuery type, double threshold) {
    this.threshold = threshold;
    this.type = type;
  }

  @Override
  public boolean isSolved(Bounds bounds) {
    switch (type) {
      case GREATER_OR_EQUAL:
      case LESS_THAN:
        return threshold <= bounds.lowerBound() || bounds.upperBound() < threshold;
      case GREATER_THAN:
      case LESS_OR_EQUAL:
        return threshold < bounds.lowerBound() || bounds.upperBound() <= threshold;
      default:
        throw new AssertionError();
    }
  }

  @Override
  public Boolean interpret(Bounds bounds) {
    assert isSolved(bounds);

    switch (type) {
      case GREATER_OR_EQUAL:
        return threshold <= bounds.lowerBound();
      case GREATER_THAN:
        return threshold < bounds.lowerBound();
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
