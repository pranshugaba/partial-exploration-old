package de.tum.in.pet.values;

import org.immutables.value.Value;
import prism.PrismUtils;

public abstract class Bounds {
  public static Bounds of(double lower, double upper) {
    if (lower == upper) {
      return of(lower);
    }
    return ValueBoundsTuple.create(lower, upper);
  }

  public static Bounds of(double value) {
    return ValueBoundsTuple.create(value, value);
  }

  public static Bounds unknown() {
    return ValueBounds.UNKNOWN;
  }


  public static Bounds reach(double lower, double upper) {
    if (lower == upper) {
      return of(lower);
    }
    if (lower == 0.0d && upper == 1.0d) {
      return reachUnknown();
    }
    return ReachabilityBoundsTuple.create(lower, upper);
  }

  public static Bounds reach(double value) {
    if (value == 0.0d) {
      return reachZero();
    }
    if (value == 1.0d) {
      return reachOne();
    }
    return ReachabilityBoundsTuple.create(value, value);
  }

  public static Bounds reachZero() {
    return ReachabilityBounds.ZERO;
  }

  public static Bounds reachOne() {
    return ReachabilityBounds.ONE;
  }

  public static Bounds reachUnknown() {
    return ReachabilityBounds.UNKNOWN;
  }


  public abstract double lowerBound();

  public abstract double upperBound();


  public double difference() {
    return upperBound() - lowerBound();
  }

  public double average() {
    return (lowerBound() + upperBound()) / 2;
  }

  public boolean contains(Bounds other) {
    return lowerBound() <= other.lowerBound() && other.upperBound() <= upperBound();
  }

  public boolean equalsUpTo(Bounds other) {
    return PrismUtils.doublesAreEqual(lowerBound(), other.lowerBound())
        && PrismUtils.doublesAreEqual(upperBound(), other.upperBound());
  }


  public abstract Bounds withUpper(double upperBound);

  public abstract Bounds withLower(double lowerBound);


  @Value.Check
  protected void check() {
    assert lowerBound() <= upperBound();
  }
}
