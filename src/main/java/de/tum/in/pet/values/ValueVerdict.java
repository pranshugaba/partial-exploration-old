package de.tum.in.pet.values;

import de.tum.in.pet.implementation.reachability.QualitativeQuery;

public interface ValueVerdict<R> {
  boolean isSolved(Bounds bounds);

  R interpret(Bounds bounds);

  class Qualitative implements ValueVerdict<Boolean> {
    private final QualitativeQuery type;
    private final double threshold;

    public Qualitative(QualitativeQuery type, double threshold) {
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

  class Quantitative implements ValueVerdict<Double> {
    private final double precision;
    private final boolean relativeError;

    public Quantitative(double precision, boolean relativeError) {
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

}
