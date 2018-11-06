package de.tum.in.prism.core.bounds;


import de.tum.in.prism.util.annotation.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class StateBounds {
  public static final StateBounds ZERO = of(0.0d);
  public static final StateBounds ONE = of(1.0d);
  public static final StateBounds ZERO_ONE = of(0.0d, 1.0d);


  abstract double upperBound();

  abstract double lowerBound();


  public static StateBounds of(double lower, double upper) {
    return StateBoundsTuple.create(lower, upper);
  }

  public static StateBounds of(double value) {
    return StateBoundsTuple.create(value, value);
  }


  @Override
  public String toString() {
    return String.format("[%.2g,%.2g]", lowerBound(), upperBound());
  }


  double difference() {
    assert upperBound() >= lowerBound();
    return upperBound() - lowerBound();
  }

  StateBounds withUpper(double upperBound) {
    return of(lowerBound(), upperBound);
  }

  StateBounds withLower(double lowerBound) {
    return of(lowerBound, upperBound());
  }

  @Value.Check
  protected void check() {
    assert lowerBound() <= upperBound();
  }
}
