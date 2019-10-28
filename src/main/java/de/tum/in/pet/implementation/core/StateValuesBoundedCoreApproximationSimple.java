package de.tum.in.pet.implementation.core;

import static de.tum.in.pet.util.Util.isEqual;
import static de.tum.in.pet.util.Util.isOne;
import static de.tum.in.pet.util.Util.isZero;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.function.IntPredicate;

public class StateValuesBoundedCoreApproximationSimple extends StateValuesBoundedCoreAbstract
    implements ApproximationMarker {
  private static final int ONE_STEP_THRESHOLD = 4;

  private final Int2ObjectMap<double[]> stateBounds;
  private final int approximationWidth;

  public StateValuesBoundedCoreApproximationSimple(IntPredicate exploredState,
      int approximationWidth) {
    super(exploredState);
    assert ONE_STEP_THRESHOLD < approximationWidth;
    this.stateBounds = new Int2ObjectOpenHashMap<>();
    this.approximationWidth = approximationWidth;
  }

  @Override
  public double upperBound(int state, int remainingSteps) {
    if (!isExploredState(state)) {
      return 1.0d;
    }
    if (isZeroState(state) || remainingSteps <= 0) {
      return 0.0d;
    }

    double[] values = stateBounds.get(state);
    if (values == null) {
      return 1.0d;
    }
    assert !isZeroState(state);

    int offset = getOffset(remainingSteps);
    return offset < values.length ? values[offset] : 1.0d;
  }

  @Override
  public void setUpperBound(int state, int remainingSteps, double value) {
    assert isExploredState(state);

    if (remainingSteps <= 0) {
      return;
    }
    if (isOne(value)) {
      assert isOne(upperBound(state, remainingSteps));
      return;
    }
    if (isZero(value)) {
      setZero(state, remainingSteps);
      return;
    }

    assert !isZeroState(state);

    if (!isApproximationPoint(remainingSteps)) {
      return;
    }
    int offset = getOffset(remainingSteps);

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
    if (isEqual(value, oldValue) && oldValue <= value) {
      return;
    }
    // Check monotonicity of added value
    //assert value <= oldValue : "Updating from " + oldValue + " to " + value;
    //values[offset] = value;

    // Maintain monotonicity
    for (int i = 0; i <= offset; i++) {
      if (values[i] > value) {
        values[i] = value;
      }
    }
  }

  private int getOffset(int remainingSteps) {
    return remainingSteps < ONE_STEP_THRESHOLD
        ? remainingSteps
        : (remainingSteps + approximationWidth - 1) / approximationWidth + ONE_STEP_THRESHOLD;
  }

  private boolean isApproximationPoint(int remainingSteps) {
    boolean value = remainingSteps < ONE_STEP_THRESHOLD
        || (remainingSteps % approximationWidth == 0);
    assert value == (getOffset(remainingSteps) < getOffset(remainingSteps + 1));
    return value;
  }
}
