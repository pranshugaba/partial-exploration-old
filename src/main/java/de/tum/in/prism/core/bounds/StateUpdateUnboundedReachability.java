package de.tum.in.prism.core.bounds;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.util.Distribution;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class StateUpdateUnboundedReachability implements StateUpdateUnbounded {
  private final Int2ObjectMap<StateBounds> bounds = new Int2ObjectOpenHashMap<>();
  private final NatBitSet zeroStates = NatBitSets.set();
  private double precision = 1.0d;

  @Override
  public double getPrecision() {
    return precision;
  }

  @Override
  public void setPrecision(double newPrecision) {
    if (newPrecision == precision) {
      return;
    }
    precision = newPrecision;
  }


  @Override
  public StateBounds getBounds(int state) {
    if (zeroStates.contains(state)) {
      return StateBounds.ZERO;
    }
    return bounds.getOrDefault(state, StateBounds.ZERO_ONE);
  }


  @Override
  public double getUpperBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::getUpperBound, state);
  }

  @Override
  public double getLowerBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::getUpperBound, state);
  }


  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    assert 0 <= lowerBound && lowerBound <= upperBound && upperBound <= 1.0d;

    if (upperBound == 0.0d) {
      setZero(state);
      return;
    }

    StateBounds newBounds = StateBounds.of(lowerBound, upperBound);
    StateBounds oldBounds = bounds.put(state, newBounds);
    assert oldBounds.lowerBound() <= newBounds.lowerBound();
    assert newBounds.upperBound() <= oldBounds.upperBound();
  }


  @Override
  public void setZero(int state) {
    bounds.remove(state);
    zeroStates.set(state);
    assert getUpperBound(state) == 0.0d;
  }


  @Override
  public void clear(int state) {
    zeroStates.clear(state);
    bounds.remove(state);
    assert getUpperBound(state) == 1.0d;
  }
}
