package de.tum.in.pet.implementation.core;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.StateValues;
import de.tum.in.pet.values.StateValuesBounded;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;

public abstract class StateValuesBoundedCoreAbstract implements StateValuesBounded {
  private final Int2IntMap zeroStep = new Int2IntLinkedOpenHashMap();
  private final NatBitSet zeroStates = NatBitSets.set();

  @Override
  public void setZero(int state) {
    zeroStep.remove(state);
    zeroStates.set(state);
  }

  public void setZero(int state, int remainingSteps) {
    if (remainingSteps <= 0) {
      return;
    }
    zeroStep.merge(state, remainingSteps, Integer::max);
  }

  public boolean isZeroDifference(int state, int remainingSteps) {
    return remainingSteps <= 0 || zeroStates.contains(state)
        || remainingSteps <= zeroStep.getOrDefault(state, 0);
  }

  @Override
  public void clear(int state) {
    throw new UnsupportedOperationException();
  }

  protected boolean isZeroState(int state) {
    return zeroStates.contains(state);
  }

  public abstract static class StepValuesAbstract<T extends StateValuesBoundedCoreAbstract>
      implements StateValues {
    protected final T values;
    protected final int remainingSteps;

    public StepValuesAbstract(T values, int remainingSteps) {
      this.values = values;
      this.remainingSteps = remainingSteps;
    }

    @Override
    public void setZero(int state) {
      values.setZero(state, remainingSteps);
    }

    @Override
    public boolean isZeroDifference(int state) {
      return values.isZeroDifference(state, remainingSteps);
    }

  }
}
