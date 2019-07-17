package de.tum.in.pet.values;

import de.tum.in.pet.util.annotation.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
abstract class ReachabilityBounds extends Bounds {
  @Override
  public Bounds withUpper(double upperBound) {
    return of(lowerBound(), upperBound);
  }

  @Override
  public Bounds withLower(double lowerBound) {
    return of(lowerBound, upperBound());
  }

  @Override
  public String toString() {
    if (lowerBound() == 0.0d && upperBound() == 1.0d) {
      return "[?]";
    }
    if (lowerBound() == upperBound()) {
      return String.format("=%.5g", lowerBound());
    }
    if (lowerBound() == 0.0d) {
      return String.format("<%.5g", upperBound());
    }
    if (upperBound() == 1.0d) {
      return String.format(">%.5g", lowerBound());
    }
    return String.format("[%.5g,%.5g]", lowerBound(), upperBound());
  }


  @Override
  protected void check() {
    super.check();
    assert 0.0d <= lowerBound() && upperBound() <= 1.0d;
  }
}
