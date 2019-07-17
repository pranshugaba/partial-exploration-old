package de.tum.in.pet.values;

final class BoundsSingletons {
  static final ReachabilityBounds ZERO = ReachabilityBoundsTuple.create(0.0d, 0.0d);
  static final ReachabilityBounds UNKNOWN = ReachabilityBoundsTuple.create(0.0d, 1.0d);
  static final ReachabilityBounds ONE = ReachabilityBoundsTuple.create(1.0d, 1.0d);
  static final Bounds UNKNOWN_VALUES =
      ValueBoundsTuple.of(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

  private BoundsSingletons() {
    // Empty
  }
}
