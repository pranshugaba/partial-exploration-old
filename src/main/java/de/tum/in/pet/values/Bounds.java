package de.tum.in.pet.values;

import static de.tum.in.probmodels.util.Util.isEqual;
import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;
import static de.tum.in.probmodels.util.Util.lessOrEqual;

import org.immutables.value.Value;

public abstract class Bounds {
  public static Bounds of(double lower, double upper) {
    assert lower <= upper;
    if (isEqual(lower, upper)) {
      return of(lower);
    }
    return ValueBoundsTuple.create(lower, upper);
  }

  public static Bounds of(double value) {
    return ValueBoundsTuple.create(value, value);
  }

  public static Bounds unknown() {
    return BoundsSingletons.UNKNOWN_VALUES;
  }


  public static Bounds reach(double lower, double upper) {
    assert lower <= upper;
    if (isOne(lower)) {
      return reachOne();
    }
    if (isZero(upper)) {
      return reachZero();
    }
    if (isEqual(lower, upper)) {
      return of(lower);
    }
    if (isZero(lower) && isOne(upper)) {
      return reachUnknown();
    }
    return ReachabilityBoundsTuple.create(lower, upper);
  }

  public static Bounds reach(double value) {
    if (isZero(value)) {
      return reachZero();
    }
    if (isOne(value)) {
      return reachOne();
    }
    return ReachabilityBoundsTuple.create(value, value);
  }

  public static Bounds reachZero() {
    return BoundsSingletons.ZERO;
  }

  public static Bounds reachOne() {
    return BoundsSingletons.ONE;
  }

  public static Bounds reachUnknown() {
    return BoundsSingletons.UNKNOWN;
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
    return lessOrEqual(lowerBound(), other.lowerBound())
        && lessOrEqual(other.upperBound(), upperBound());
  }

  public boolean equalsUpTo(Bounds other) {
    return isEqual(lowerBound(), other.lowerBound())
        && isEqual(upperBound(), other.upperBound());
  }


  public abstract Bounds withUpper(double upperBound);

  public abstract Bounds withLower(double lowerBound);


  @Value.Check
  protected void check() {
    assert lowerBound() <= upperBound();
  }
}
