package de.tum.in.pet.implementation.reachability;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.StateValues;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class StateValuesUnboundedReachability implements StateValues {
  private final Int2ObjectMap<Bounds> bounds = new Int2ObjectOpenHashMap<>();
  private final NatBitSet zeroStates = NatBitSets.set();
  private final NatBitSet oneStates = NatBitSets.set();

  @Override
  public Bounds bounds(int state) {
    if (zeroStates.contains(state)) {
      return Bounds.ZERO_ZERO;
    }
    if (oneStates.contains(state)) {
      return Bounds.ONE_ONE;
    }
    return bounds.getOrDefault(state, Bounds.ZERO_ONE);
  }


  @Override
  public double upperBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::upperBound, state);
  }

  @Override
  public double lowerBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::lowerBound, state);
  }


  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    assert 0 <= lowerBound && lowerBound <= upperBound && upperBound <= 1.0d;

    if (upperBound == 0.0d) {
      setZero(state);
      return;
    }
    if (lowerBound == 1.0d) {
      setOne(state);
      return;
    }

    Bounds newBounds = Bounds.of(lowerBound, upperBound);
    Bounds oldBounds = bounds.put(state, newBounds);
    assert oldBounds == null || (oldBounds.lowerBound() <= newBounds.lowerBound()
        && newBounds.upperBound() <= oldBounds.upperBound()) :
        "Updating from " + oldBounds + " to " + newBounds;
  }


  @Override
  public void setZero(int state) {
    bounds.remove(state);
    zeroStates.set(state);
    assert bounds(state).equals(Bounds.ZERO_ZERO);
  }

  @Override
  public void setOne(int state) {
    bounds.remove(state);
    oneStates.set(state);
    assert bounds(state).equals(Bounds.ONE_ONE);
  }

  @Override
  public void clear(int state) {
    zeroStates.clear(state);
    oneStates.clear(state);
    bounds.remove(state);
    assert bounds(state).equals(Bounds.ZERO_ONE);
  }
}
