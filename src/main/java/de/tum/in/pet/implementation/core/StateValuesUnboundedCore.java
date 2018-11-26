package de.tum.in.pet.implementation.core;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.util.Util;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.StateValues;
import it.unimi.dsi.fastutil.ints.Int2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class StateValuesUnboundedCore implements StateValues {
  private final Int2DoubleMap bounds = new Int2DoubleLinkedOpenHashMap();
  private final IntSet zeroStates = new IntOpenHashSet();

  public StateValuesUnboundedCore() {
    bounds.defaultReturnValue(1.0d);
  }

  @Override
  public Bounds bounds(int state) {
    double upperBound = upperBound(state);
    return upperBound == 0.0d ? Bounds.ZERO_ZERO : Bounds.of(0.0d, upperBound);
  }


  @Override
  public double upperBound(int state) {
    if (zeroStates.contains(state)) {
      return 0.0d;
    }
    return bounds.getOrDefault(state, 1.0d);
  }

  @Override
  public double upperBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::upperBound, state);
  }

  @Override
  public double lowerBound(int state) {
    return 0.0d;
  }

  @Override
  public double difference(int state) {
    return upperBound(state);
  }


  @Override
  public boolean isZeroDifference(int state) {
    return zeroStates.contains(state);
  }


  @Override
  public void setUpperBound(int state, double value) {
    assert 0 <= value && value <= 1.0d
        : "Value " + String.format("%.6g", value) + " not within bounds";
    if (value == 1.0d) {
      assert !bounds.containsKey(state);
      return;
    }
    if (value == 0.0d) {
      setZero(state);
      return;
    }

    double oldValue = bounds.put(state, value);
    assert Util.doublesAreLessOrEqual(value, oldValue) : "Value " + String.format("%.6g", value)
        + " larger than old value " + String.format("%.6g", oldValue);
  }

  @Override
  public void setLowerBound(int state, double value) {
    checkArgument(value == 0.0d, "Non-zero lower bound %s", value);
  }

  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    checkArgument(lowerBound == 0.0d, "Non-zero lower bound %s", lowerBound);
    setUpperBound(state, upperBound);
  }


  @Override
  public void setZero(int state) {
    bounds.remove(state);
    zeroStates.add(state);
    assert upperBound(state) == 0.0d;
  }

  @Override
  public void setOne(int state) {
    checkArgument(upperBound(state) == 1.0d);
  }


  @Override
  public void clear(int state) {
    zeroStates.remove(state);
    bounds.remove(state);
    assert upperBound(state) == 1.0d;
  }
}
