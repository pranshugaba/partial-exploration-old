package de.tum.in.prism.core.bounds;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import prism.PrismUtils;

public class StateUpdateBoundedCoreApproximationDense
    extends StateUpdateBoundedCoreApproximationAbstract {
  private final Int2ObjectMap<double[]> stateBounds;

  public StateUpdateBoundedCoreApproximationDense() {
    stateBounds = new Int2ObjectOpenHashMap<>();
  }


  @Override
  public double getUpperBound(int state, int remainingSteps) {
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
      assert PrismUtils.doublesAreEqual(getUpperBound(state, remainingSteps), 1.0d);
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
      Arrays.fill(values, 0, remainingSteps + 1, value);
      stateBounds.put(state, values);
      return;
    }
    if (values.length <= remainingSteps) {
      int oldLength = values.length;
      int newLength = Math.max(oldLength * 2, remainingSteps + 1);
      values = Arrays.copyOf(values, newLength);
      Arrays.fill(values, oldLength, remainingSteps + 1, value);
      Arrays.fill(values, remainingSteps + 1, newLength, 1.0d);
      stateBounds.put(state, values);
      return;
    }
    double oldValue = values[remainingSteps];
    if (PrismUtils.doublesAreEqual(value, oldValue) && oldValue <= value) {
      return;
    }
    // Check monotonicity of added value
    assert value <= oldValue;
    values[remainingSteps] = value;

    // Maintain monotonicity
    for (int i = 0; i < remainingSteps; i++) {
      if (values[remainingSteps] > value) {
        values[remainingSteps] = value;
      }
    }
  }

  @Override
  public void setZero(int state) {
    super.setZero(state);
    stateBounds.remove(state);
  }

  @Override
  public void setOne(int state) {
    checkArgument(!isZeroState(state) && !stateBounds.containsKey(state));
  }
}
