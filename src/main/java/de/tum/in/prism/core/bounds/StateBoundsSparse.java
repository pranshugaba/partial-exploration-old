package de.tum.in.prism.core.bounds;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import it.unimi.dsi.fastutil.ints.Int2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import prism.PrismUtils;

public class StateBoundsSparse implements StateBounds {
  // Can also be implemented as array, but this enables a sparse representation, saving memory
  private final Int2DoubleMap reachabilityUpperBounds = new Int2DoubleLinkedOpenHashMap();
  private final NatBitSet solvedStates = NatBitSets.set();
  private final NatBitSet zeroStates = NatBitSets.set();
  private double precision = 1.0d;

  public StateBoundsSparse() {
    reachabilityUpperBounds.defaultReturnValue(1.0d);
  }

  @Override
  public double getUpperBound(int state) {
    if (zeroStates.contains(state)) {
      return 0.0d;
    }
    return reachabilityUpperBounds.getOrDefault(state, 1.0d);
  }

  @Override
  public boolean isSolved(int state) {
    return solvedStates.contains(state);
  }

  @Override
  public boolean isZero(int state) {
    return zeroStates.contains(state);
  }

  @Override
  public void setUpperBound(int state, double value) {
    assert 0 <= value && (value <= 1.0d || PrismUtils.doublesAreEqual(value, 1.0d));
    if (value == 0.0d) {
      setZero(state);
    }
    if (value < precision) {
      solvedStates.set(state);
    }

    double oldValue = reachabilityUpperBounds.put(state, value);
    assert value <= oldValue || PrismUtils.doublesAreEqual(value, oldValue);
  }

  @Override
  public void setZero(int state) {
    reachabilityUpperBounds.remove(state);
    solvedStates.set(state);
    zeroStates.set(state);
    assert getUpperBound(state) == 0.0d;
  }

  @Override
  public void setPrecision(double newPrecision) {
    if (newPrecision == precision) {
      return;
    }
    if (newPrecision != precision) {
      reachabilityUpperBounds.int2DoubleEntrySet().forEach(entry ->
          solvedStates.set(entry.getIntKey(), entry.getDoubleValue() < newPrecision));
    }
    precision = newPrecision;
  }

  @Override
  public void drop(int state) {
    zeroStates.clear(state);
    solvedStates.clear(state);
    reachabilityUpperBounds.remove(state);
    assert getUpperBound(state) == 1.0d;
  }
}
