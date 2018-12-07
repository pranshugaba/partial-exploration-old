package de.tum.in.pet.implementation.core;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.bounded.StateValuesBounded;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.function.IntPredicate;

public abstract class StateValuesBoundedCoreAbstract implements StateValuesBounded {
  private final IntPredicate exploredState;
  private final Int2IntMap zeroStep = new Int2IntLinkedOpenHashMap();
  private final NatBitSet zeroStates = NatBitSets.set();

  protected StateValuesBoundedCoreAbstract(IntPredicate exploredState) {
    this.exploredState = exploredState;
  }

  @Override
  public Bounds bounds(int state, int remainingSteps) {
    return Bounds.of(0.0d, upperBound(state, remainingSteps));
  }

  @Override
  public void setBounds(int state, int remainingSteps, double lowerBound, double upperBound) {
    checkArgument(lowerBound == 0.0d);
    setUpperBound(state, remainingSteps, upperBound);
  }

  @Override
  public void setBounds(int state, int remainingSteps, Bounds bounds) {
    checkArgument(bounds.lowerBound() == 0.0d);
    setUpperBound(state, remainingSteps, bounds.upperBound());
  }

  public void setZero(int state, int remainingSteps) {
    if (remainingSteps <= 0) {
      return;
    }
    zeroStep.merge(state, remainingSteps, Integer::max);
  }

  @Override
  public boolean isZeroDifference(int state, int remainingSteps) {
    return isZeroUpperBound(state, remainingSteps);
  }

  @Override
  public boolean isZeroUpperBound(int state, int remainingSteps) {
    return remainingSteps <= 0 || zeroStates.contains(state)
        || remainingSteps <= zeroStep.getOrDefault(state, 0);
  }

  @Override
  public boolean isOneLowerBound(int state, int remainingSteps) {
    return false;
  }

  protected boolean isExploredState(int state) {
    return exploredState.test(state);
  }

  protected boolean isZeroState(int state) {
    return zeroStates.contains(state);
  }
}
