package de.tum.in.prism.core.bounds;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.util.Distribution;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;

public abstract class StateUpdateBoundedCoreApproximationAbstract implements StateUpdateBounded {
  private double precision = 1.0d;
  private final Int2IntMap zeroStep = new Int2IntLinkedOpenHashMap();
  private final NatBitSet zeroStates = NatBitSets.set();

  @Override
  public double getPrecision() {
    return precision;
  }

  @Override
  public void setPrecision(double precision) {
    this.precision = precision;
  }


  @Override
  public StateBounds getBounds(int state, int remainingSteps) {
    return StateBounds.of(0.0d, getUpperBound(state, remainingSteps));
  }

  @Override
  public double getLowerBound(int state, int remainingSteps) {
    return 0.0d;
  }

  @Override
  public double getLowerBound(int state, int remainingSteps, Distribution distribution) {
    return 0.0d;
  }

  @Override
  public void setLowerBound(int state, int remainingSteps, double value) {
    checkArgument(value == 0.0d);
  }

  @Override
  public void setBounds(int state, int remainingSteps, double lowerBound, double upperBound) {
    checkArgument(lowerBound == 0.0d);
    setUpperBound(state, remainingSteps, upperBound);
  }

  @Override
  public void setZero(int state) {
    zeroStep.remove(state);
    zeroStates.set(state);
  }


  @Override
  public boolean isSolved(int state, int remainingSteps) {
    return isZeroDifference(state, remainingSteps)
        || getUpperBound(state, remainingSteps) < getPrecision();
  }

  @Override
  public boolean isZeroDifference(int state, int remainingSteps) {
    return remainingSteps <= 0 || zeroStates.contains(state)
        || remainingSteps <= zeroStep.getOrDefault(state, 0);
  }

  @Override
  public void setZero(int state, int remainingSteps) {
    if (remainingSteps <= 0) {
      return;
    }
    zeroStep.merge(state, remainingSteps, Integer::max);
  }

  @Override
  public void clear(int state) {
    throw new UnsupportedOperationException();
  }

  protected boolean isZeroState(int state) {
    return zeroStates.contains(state);
  }
}
