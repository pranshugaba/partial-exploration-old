package de.tum.in.prism.core.bounds;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import prism.PrismUtils;

public class StepBoundApproximationSimple implements StepBoundApproximation {
  private static final int ONE_STEP_THRESHOLD = 4;

  private final Int2IntMap zeroStep;
  private final Int2ObjectMap<double[]> stateBounds;
  private final NatBitSet zeroStates;
  private final int approximationWidth;

  public StepBoundApproximationSimple(int approximationWidth) {
    this.zeroStep = new Int2IntLinkedOpenHashMap();
    this.stateBounds = new Int2ObjectOpenHashMap<>();
    this.approximationWidth = approximationWidth;
    zeroStates = NatBitSets.set();
  }

  @Override
  public double getUpperBound(int state, int remainingSteps) {
    if (remainingSteps <= 0) {
      return 0.0d;
    }
    if (isZero(state, remainingSteps)) {
      return 0.0d;
    }

    double[] values = stateBounds.get(state);
    if (values == null) {
      return 1.0d;
    }
    assert !zeroStates.contains(state);

    int offset = getOffset(state, remainingSteps);
    return offset < values.length ? values[offset] : 1.0d;
  }

  @Override
  public void setZero(int state) {
    stateBounds.remove(state);
    zeroStep.remove(state);
    zeroStates.set(state);
  }

  @Override
  public void setZero(int state, int remainingSteps) {
    if (remainingSteps <= 0) {
      return;
    }
    zeroStep.merge(state, remainingSteps, Integer::max);
  }

  @Override
  public boolean isZero(int state, int remainingSteps) {
    return remainingSteps == 0 || zeroStates.contains(state) || remainingSteps <= zeroStep
        .getOrDefault(state, 0);
  }

  @Override
  public void setUpperBound(int state, int remainingSteps, double value) {
    if (remainingSteps <= 0) {
      return;
    }
    setValue(state, remainingSteps, value);
  }

  private int getOffset(int state, int remainingSteps) {
    return remainingSteps < ONE_STEP_THRESHOLD
        ? remainingSteps
        : (remainingSteps - ONE_STEP_THRESHOLD) / approximationWidth + ONE_STEP_THRESHOLD;
  }

  private boolean isApproximationPoint(int state, int remainingSteps) {
    boolean value = remainingSteps < ONE_STEP_THRESHOLD || (
        (remainingSteps - ONE_STEP_THRESHOLD + 1) % approximationWidth == 0);
    assert value == (getOffset(state, remainingSteps) < getOffset(state, remainingSteps + 1));
    return value;
  }

  private void setValue(int state, int remainingSteps, double value) {
    if (PrismUtils.doublesAreEqual(value, 1.0d)) {
      return;
    }
    if (value == 0.0d) {
      setZero(state, remainingSteps);
      return;
    }

    assert remainingSteps > 0;
    assert !zeroStates.contains(state);

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
    assert value <= oldValue : state + " " + value + " " + oldValue;
    values[offset] = value;

    // Maintain monotonicity
    for (int i = 0; i < offset; i++) {
      if (values[offset] > value) {
        values[offset] = value;
      }
    }
  }

  @Override
  public void setUpperBounds(IntSet states, Int2DoubleFunction values, int remainingSteps) {
    if (remainingSteps <= 0) {
      return;
    }
    states.forEach((int state) -> setValue(state, remainingSteps, values.applyAsDouble(state)));
  }

  public String printBounds(IntSet states, int maxSteps) {
    StringBuilder builder = new StringBuilder(approximationWidth * 10 * states.size());
    builder.append("Bounds: ").append(maxSteps).append(" / ").append(approximationWidth)
        .append('\n');

    states.forEach((int state) -> {
      int solvedUntil = zeroStep.getOrDefault(state, 0);
      builder.append(String.format("%5d(%4d): ", state, solvedUntil));

      for (int step = solvedUntil + 1; step <= maxSteps; step++) {
        if (isApproximationPoint(state, step)) {
          double upperBound = getUpperBound(state, step);
          builder.append(String.format(" %4d: %6f", step, upperBound));
          if (PrismUtils.doublesAreEqual(upperBound, 1.0d)) {
            break;
          }
        }
      }
      builder.append('\n');
    });
    return builder.toString();
  }
}
