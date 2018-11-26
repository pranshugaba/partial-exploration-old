package de.tum.in.pet.values;

import de.tum.in.pet.util.annotation.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class Bounds {
  public static final Bounds ZERO_ZERO;
  public static final Bounds ZERO_ONE;
  public static final Bounds ONE_ONE;

  static {
    ZERO_ZERO = BoundsTuple.create(0.0d, 0.0d);
    ZERO_ONE = BoundsTuple.create(0.0d, 1.0d);
    ONE_ONE = BoundsTuple.create(1.0d, 1.0d);
  }


  public abstract double lowerBound();

  public abstract double upperBound();


  public static Bounds of(double lower, double upper) {
    if (lower == upper) {
      return of(lower);
    }
    if (lower == 0.0d && upper == 1.0d) {
      return ZERO_ONE;
    }
    return BoundsTuple.create(lower, upper);
  }

  public static Bounds of(double value) {
    if (value == 0.0d) {
      return ZERO_ZERO;
    }
    if (value == 1.0d) {
      return ONE_ONE;
    }
    return BoundsTuple.create(value, value);
  }


  @Override
  public String toString() {
    if (lowerBound() == 0.0d && upperBound() == 1.0d) {
      return "[?]";
    }
    if (lowerBound() == upperBound()) {
      return String.format("=%.3g", lowerBound());
    }

    return String.format("[%.3g,%.3g]", lowerBound(), upperBound());
  }


  public double difference() {
    assert upperBound() >= lowerBound();
    return upperBound() - lowerBound();
  }

  public double average() {
    return (lowerBound() + upperBound()) / 2;
  }


  public Bounds withUpper(double upperBound) {
    return of(lowerBound(), upperBound);
  }

  public Bounds withLower(double lowerBound) {
    return of(lowerBound, upperBound());
  }


  @Value.Check
  protected void check() {
    assert lowerBound() <= upperBound();
  }
}
