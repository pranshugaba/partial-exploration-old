package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.util.Util;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.unbounded.StateValues;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.IntPredicate;

public class StateValuesReachability implements StateValues {
  private final ReachabilityBounds reachability;
  private final Int2ObjectMap<Bounds> bounds = new Int2ObjectOpenHashMap<>();

  public StateValuesReachability(IntPredicate target) {
    this.reachability = new ReachabilityBounds(target);
  }

  @Override
  public Bounds bounds(int state) {
    Bounds reachabilityBounds = reachability.get(state);
    if (reachabilityBounds != null) {
      return reachabilityBounds;
    }
    Bounds storedBounds = this.bounds.get(state);
    return storedBounds == null ? Bounds.reachUnknown() : storedBounds;
  }

  @Override
  public Bounds bounds(int state, Distribution distribution) {
    return Bounds.reach(lowerBound(state, distribution), upperBound(state, distribution));
  }

  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    assert 0 <= lowerBound && lowerBound <= upperBound && upperBound <= 1.0d;
    assert reachability.isKnown(state);
    assert Util.lessOrEqual(lowerBound(state), lowerBound)
        && Util.lessOrEqual(upperBound, upperBound(state));

    if (reachability.set(state, lowerBound, upperBound)) {
      bounds.remove(state);
    } else {
      bounds.put(state, Bounds.of(lowerBound, upperBound));
    }
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
  public void clear(int state) {
    reachability.clear(state);
    bounds.remove(state);
  }
}
