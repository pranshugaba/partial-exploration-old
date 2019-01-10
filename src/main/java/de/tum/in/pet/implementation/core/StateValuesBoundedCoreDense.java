package de.tum.in.pet.implementation.core;

import de.tum.in.pet.util.Util;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.function.IntPredicate;
import prism.PrismUtils;

public class StateValuesBoundedCoreDense extends StateValuesBoundedCoreAbstract {
  private final Int2ObjectMap<double[]> stateBounds;

  public StateValuesBoundedCoreDense(IntPredicate exploredState) {
    super(exploredState);
    stateBounds = new Int2ObjectOpenHashMap<>();
  }

  @Override
  public double upperBound(int state, int remainingSteps) {
    if (!isExploredState(state)) {
      return 1.0d;
    }
    if (isZeroDifference(state, remainingSteps)) {
      return 0.0d;
    }

    double[] values = stateBounds.get(state);
    if (values == null) {
      return 1.0d;
    }
    assert !isZeroState(state);

    return remainingSteps < values.length ? values[remainingSteps] : 1.0d;
  }

  @Override
  public void setUpperBound(int state, int remainingSteps, double value) {
    if (remainingSteps <= 0) {
      return;
    }
    if (PrismUtils.doublesAreEqual(value, 1.0d)) {
      assert PrismUtils.doublesAreEqual(upperBound(state, remainingSteps), 1.0d);
      return;
    }
    if (value == 0.0d) {
      setZero(state, remainingSteps);
      return;
    }

    assert !isZeroState(state);

    double[] values = stateBounds.get(state);

    if (values == null) {
      values = new double[remainingSteps + 1];
      Arrays.fill(values, value);
      stateBounds.put(state, values);
      return;
    }

    int monotonicityUpdate;
    if (values.length <= remainingSteps) {
      int oldLength = values.length;
      int newLength = Math.max(oldLength * 2, remainingSteps + 1);

      values = Arrays.copyOf(values, newLength);
      Arrays.fill(values, oldLength, remainingSteps + 1, value);
      Arrays.fill(values, remainingSteps + 1, newLength, 1.0d);
      monotonicityUpdate = oldLength;

      stateBounds.put(state, values);
    } else {
      double oldValue = values[remainingSteps];
      // Check monotonicity of added value
      assert Util.lessOrEqual(value, oldValue) : "Updating " + oldValue + " to " + value;
      if (PrismUtils.doublesAreEqual(value, oldValue)) {
        return;
      }
      values[remainingSteps] = value;
      monotonicityUpdate = remainingSteps;
    }

    for (int i = 0; i < monotonicityUpdate; i++) {
      if (values[i] > value) {
        values[i] = value;
      }
    }
  }
}
