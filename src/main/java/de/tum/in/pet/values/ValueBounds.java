package de.tum.in.pet.values;

import de.tum.in.pet.util.annotation.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
abstract class ValueBounds extends Bounds {
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
    return lowerBound() == upperBound()
        ? String.format("=%.5g", lowerBound())
        : String.format("[%.5g,%.5g]", lowerBound(), upperBound());
  }
}
