package de.tum.in.pet.implementation.core;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.StateValues;
import de.tum.in.pet.values.StateValuesFixed;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import prism.PrismUtils;

public class StateValuesBoundedCoreDense extends StateValuesBoundedCoreAbstract {
  private final Int2ObjectMap<double[]> stateBounds;

  public StateValuesBoundedCoreDense() {
    stateBounds = new Int2ObjectOpenHashMap<>();
  }


  @Override
  public StateValues stepValues(int remainingSteps) {
    if (remainingSteps <= 0) {
      return new StateValuesFixed(Bounds.ZERO_ZERO);
    }
    return new StepValues(this, remainingSteps);
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


  double getUpperBound(int state, int remainingSteps) {
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

  void setUpperBound(int state, int remainingSteps, double value) {
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

  private static class StepValues extends StepValuesAbstract<StateValuesBoundedCoreDense> {
    public StepValues(StateValuesBoundedCoreDense values, int remainingSteps) {
      super(values, remainingSteps);
    }

    @Override
    public void setBounds(int state, double lowerBound, double upperBound) {
      checkArgument(lowerBound == 0.0d);
      values.setUpperBound(state, remainingSteps, upperBound);
    }

    @Override
    public void clear(int state) {
      values.clear(state);
    }

    @Override
    public Bounds bounds(int state) {
      return Bounds.of(0.0d, values.getUpperBound(state, remainingSteps));
    }

    @Override
    public double lowerBound(int state) {
      return 0.0d;
    }

    @Override
    public double upperBound(int state) {
      return values.getUpperBound(state, remainingSteps);
    }

    @Override
    public double difference(int state) {
      return values.getUpperBound(state, remainingSteps);
    }
  }
}
