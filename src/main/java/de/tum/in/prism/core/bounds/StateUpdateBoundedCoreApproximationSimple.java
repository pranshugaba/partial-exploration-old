package de.tum.in.prism.core.bounds;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import prism.PrismUtils;

public class StateUpdateBoundedCoreApproximationSimple
    extends StateUpdateBoundedCoreApproximationAbstract {
  private static final int ONE_STEP_THRESHOLD = 4;

  private final Int2ObjectMap<double[]> stateBounds;
  private final int approximationWidth;

  public StateUpdateBoundedCoreApproximationSimple(int approximationWidth) {
    assert ONE_STEP_THRESHOLD < approximationWidth;
    this.stateBounds = new Int2ObjectOpenHashMap<>();
    this.approximationWidth = approximationWidth;
  }


  @Override
  public double getUpperBound(int state, int remainingSteps) {
    if (isZeroState(state) || remainingSteps <= 0) {
      return 0.0d;
    }

    double[] values = stateBounds.get(state);
    if (values == null) {
      return 1.0d;
    }
    assert !isZeroState(state);

    int offset = getOffset(state, remainingSteps);
    return offset < values.length ? values[offset] : 1.0d;
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

    if (!isApproximationPoint(state, remainingSteps)) {
      return;
    }
    int offset = getOffset(state, remainingSteps);

    double[] values = stateBounds.computeIfAbsent(state, s -> {
      double[] result = new double[offset + 1];
      Arrays.fill(result, value);
      return result;
    });
    if (values.length <= offset) {
      int oldLength = values.length;
      int newLength = Math.max(oldLength * 2, offset + 1);
      values = Arrays.copyOf(values, newLength);
      Arrays.fill(values, oldLength, offset + 1, value);
      Arrays.fill(values, offset + 1, newLength, 1.0d);
      stateBounds.put(state, values);
      return;
    }
    double oldValue = values[offset];
    if (PrismUtils.doublesAreEqual(value, oldValue) && oldValue <= value) {
      return;
    }
    // Check monotonicity of added value
    assert value <= oldValue;
    values[offset] = value;

    // Maintain monotonicity
    for (int i = 0; i < offset; i++) {
      if (values[offset] > value) {
        values[offset] = value;
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


  private int getOffset(int state, int remainingSteps) {
    return remainingSteps < ONE_STEP_THRESHOLD
        ? remainingSteps
        : (remainingSteps + approximationWidth - 1) / approximationWidth + ONE_STEP_THRESHOLD;
  }

  private boolean isApproximationPoint(int state, int remainingSteps) {
    boolean value = remainingSteps < ONE_STEP_THRESHOLD
        || (remainingSteps % approximationWidth == 0);
    assert value == (getOffset(state, remainingSteps) < getOffset(state, remainingSteps + 1));
    return value;
  }
}
