package de.tum.in.prism.core.bounds;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.prism.util.Distribution;
import it.unimi.dsi.fastutil.ints.Int2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class StateUpdateUnboundedCore implements StateUpdateUnbounded {
  private final Int2DoubleMap bounds = new Int2DoubleLinkedOpenHashMap();
  private final IntSet solvedStates = new IntOpenHashSet();
  private final IntSet zeroStates = new IntOpenHashSet();
  private double precision = 1.0d;

  public StateUpdateUnboundedCore() {
    bounds.defaultReturnValue(1.0d);
  }

  @Override
  public double getPrecision() {
    return precision;
  }

  @Override
  public void setPrecision(double newPrecision) {
    if (newPrecision == precision) {
      return;
    }
    bounds.int2DoubleEntrySet().forEach(entry -> {
      if (entry.getDoubleValue() < newPrecision) {
        solvedStates.add(entry.getIntKey());
      } else {
        solvedStates.remove(entry.getIntKey());
      }
    });
    precision = newPrecision;
  }


  @Override
  public StateBounds getBounds(int state) {
    double upperBound = getUpperBound(state);
    return upperBound == 0.0d ? StateBounds.ZERO : StateBounds.of(0.0d, upperBound);
  }


  @Override
  public double getUpperBound(int state) {
    if (zeroStates.contains(state)) {
      return 0.0d;
    }
    return bounds.getOrDefault(state, 1.0d);
  }

  @Override
  public double getUpperBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::getUpperBound, state);
  }

  @Override
  public double getLowerBound(int state) {
    return 0.0d;
  }

  @Override
  public double getDifference(int state) {
    return getUpperBound(state);
  }


  @Override
  public boolean isSolved(int state) {
    boolean solved = solvedStates.contains(state);
    assert solved == getUpperBound(state) < precision;
    return solved;
  }

  @Override
  public boolean isZeroDifference(int state) {
    return zeroStates.contains(state);
  }


  @Override
  public void setUpperBound(int state, double value) {
    assert 0 <= value && value <= 1.0d;
    if (value == 0.0d) {
      setZero(state);
      return;
    }
    if (value < precision) {
      solvedStates.add(state);
    }

    double oldValue = bounds.put(state, value);
    assert value <= oldValue;
  }

  @Override
  public void setLowerBound(int state, double value) {
    checkArgument(value == 0.0d);
  }

  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    checkArgument(lowerBound == 0.0d);
    setUpperBound(state, upperBound);
  }


  @Override
  public void setZero(int state) {
    bounds.remove(state);
    solvedStates.add(state);
    zeroStates.add(state);
    assert getUpperBound(state) == 0.0d;
  }

  @Override
  public void setOne(int state) {
    checkArgument(getUpperBound(state) == 1.0d);
  }


  @Override
  public void clear(int state) {
    zeroStates.remove(state);
    solvedStates.remove(state);
    bounds.remove(state);
    assert getUpperBound(state) == 1.0d;
  }
}
