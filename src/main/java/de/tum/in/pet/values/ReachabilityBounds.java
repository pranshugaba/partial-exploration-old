package de.tum.in.pet.values;

import static de.tum.in.probmodels.util.Util.isEqual;
import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;

import de.tum.in.probmodels.util.annotation.Tuple;
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
    if (isZero(lowerBound()) && isOne(upperBound())) {
      return "[?]";
    }
    if (isEqual(lowerBound(), upperBound())) {
      return String.format("=%.5g", lowerBound());
    }
    if (isZero(lowerBound())) {
      return String.format("<%.5g", upperBound());
    }
    if (isOne(upperBound())) {
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
