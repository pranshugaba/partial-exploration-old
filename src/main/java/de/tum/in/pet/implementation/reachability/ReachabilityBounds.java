package de.tum.in.pet.implementation.reachability;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;

public class ReachabilityBounds {
  private final IntPredicate target;
  private final NatBitSet knownStates = NatBitSets.set();
  private final NatBitSet oneStates = NatBitSets.set();
  private final NatBitSet zeroStates = NatBitSets.set();

  public ReachabilityBounds(IntPredicate target) {
    this.target = target;
  }

  @Nullable
  public Bounds get(int state) {
    if (knownStates.add(state)) {
      // State was not known before
      if (target.test(state)) {
        oneStates.set(state);
        return Bounds.reachOne();
      }
      return null;
    }
    if (oneStates.contains(state)) {
      return Bounds.reachOne();
    }
    if (zeroStates.contains(state)) {
      return Bounds.reachZero();
    }
    return null;
  }

  public boolean set(int state, double lowerBound, double upperBound) {
    if (upperBound == 0.0d) {
      zeroStates.set(state);
      return true;
    }
    if (lowerBound == 1.0d) {
      oneStates.set(state);
      return true;
    }
    return false;
  }

  public boolean isKnown(int state) {
    return knownStates.contains(state);
  }

  public void clear(int state) {
    knownStates.clear(state);
    zeroStates.clear(state);
    oneStates.clear(state);
  }
}
