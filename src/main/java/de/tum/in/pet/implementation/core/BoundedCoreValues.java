package de.tum.in.pet.implementation.core;

import static de.tum.in.probmodels.util.Util.isEqual;
import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.lessOrEqual;

import de.tum.in.pet.sampler.BoundedValues;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;

abstract class BoundedCoreValues implements BoundedValues {
  public final double precision;
  public final SuccessorHeuristic heuristic;
  private final IntSet exploredStates = new IntOpenHashSet();

  public BoundedCoreValues(double precision, SuccessorHeuristic heuristic) {
    this.precision = precision;
    this.heuristic = heuristic;
  }

  @Override
  public Bounds bounds(int state, int remaining) {
    return Bounds.reach(0.0d, upperBound(state, remaining));
  }

  abstract double upperBound(int state, int remaining);

  boolean isExplored(int state) {
    return exploredStates.contains(state);
  }

  @Override
  public void explored(int state, int remaining) {
    exploredStates.add(state);
  }

  @Override
  public boolean isSolved(int state, int remaining) {
    return upperBound(state, remaining) < precision;
  }

  @Override
  public int sampleNextState(int state, int remaining, List<Distribution> choices) {
    if (choices.isEmpty()) {
      return -1;
    }
    assert remaining > 0;

    IntToDoubleFunction nextStepFunction = s -> upperBound(s, remaining - 1);
    ToDoubleFunction<Integer> actionScore = i -> choices.get(i).sumWeighted(nextStepFunction);
    return SampleUtil.sampleNextState(choices, heuristic, actionScore, nextStepFunction);
  }

  @Override
  public void update(int state, int remaining, List<Distribution> choices) {
    assert isExplored(state);
    double maximalValue = 0.0d;
    for (Distribution distribution : choices) {
      double value = distribution.sumWeighted(s -> upperBound(s, remaining - 1));
      if (value > maximalValue) {
        maximalValue = value;
      }
    }
    update(state, remaining, maximalValue);
  }

  @Override
  public void update(int state, int remaining, Bounds bounds) {
    assert bounds.lowerBound() == 0.0d;
    assert lessOrEqual(bounds.upperBound(), upperBound(state, remaining));
    update(state, remaining, bounds.upperBound());
  }

  abstract void update(int state, int remaining, double bound);

  public static class Simple extends BoundedCoreValues {
    private static final int ONE_STEP_THRESHOLD = 6;

    private final Int2ObjectMap<double[]> stateBounds;
    private final int approximationWidth;

    public Simple(double precision, SuccessorHeuristic heuristic, int approximationWidth) {
      super(precision, heuristic);
      this.stateBounds = new Int2ObjectOpenHashMap<>();
      this.approximationWidth = approximationWidth;
    }

    @Override
    double upperBound(int state, int remaining) {
      if (!isExplored(state)) {
        return 1.0d;
      }
      if (remaining == 0) {
        return 0.0d;
      }

      double[] values = stateBounds.get(state);
      if (values == null) {
        return 1.0d;
      }

      int offset = offset(remaining);
      return offset < values.length ? values[offset] : 1.0d;
    }

    @Override
    public void update(int state, int remaining, double value) {
      assert isExplored(state);

      if (isOne(value)) {
        assert isOne(upperBound(state, remaining));
        return;
      }

      if (!isApproximationPoint(remaining)) {
        return;
      }
      int offset = offset(remaining);

      double[] values = stateBounds.get(state);
      if (values == null) {
        double[] newValues = new double[offset + 1];
        Arrays.fill(newValues, value);
        stateBounds.put(state, newValues);
      } else if (values.length <= offset) {
        int oldLength = values.length;
        int newLength = Math.max(oldLength * 2, offset + 1);
        double[] newValues = Arrays.copyOf(values, newLength);

        for (int i = 0; i < oldLength; i++) {
          if (newValues[i] > value) {
            newValues[i] = value;
          }
        }
        Arrays.fill(newValues, oldLength, offset + 1, value);
        Arrays.fill(newValues, offset + 1, newLength, 1.0d);
        stateBounds.put(state, newValues);
      } else {
        double oldValue = values[offset];

        if (oldValue <= value) {
          return;
        }

        // Maintain monotonicity
        for (int i = 0; i < offset; i++) {
          if (values[i] > value) {
            values[i] = value;
          }
        }
        values[offset] = value;
      }
    }

    private int offset(int remaining) {
      assert remaining > 0;
      if (remaining < ONE_STEP_THRESHOLD) {
        return remaining - 1;
      }
      int steps = remaining - ONE_STEP_THRESHOLD;
      return steps / approximationWidth + ONE_STEP_THRESHOLD - 1;
    }

    private boolean isApproximationPoint(int remaining) {
      boolean value = remaining < ONE_STEP_THRESHOLD
          || ((remaining - ONE_STEP_THRESHOLD + 1) % approximationWidth == 0);
      assert value == (offset(remaining) + 1 == offset(remaining + 1));
      return value;
    }

    @Override
    public boolean storesExact() {
      return false;
    }

    @Override
    public boolean stores(int remaining) {
      return isApproximationPoint(remaining);
    }

    @Override
    public String toString() {
      return String.format("SimpleValues(%s,%d)@%g", heuristic, approximationWidth, precision);
    }
  }

  public static class Dense extends BoundedCoreValues {
    private final Int2ObjectMap<double[]> stateBounds = new Int2ObjectOpenHashMap<>();

    public Dense(double precision, SuccessorHeuristic heuristic) {
      super(precision, heuristic);
    }

    @Override
    double upperBound(int state, int remaining) {
      if (!isExplored(state)) {
        return 1.0d;
      }
      if (remaining == 0) {
        return 0.0d;
      }
      double[] values = stateBounds.get(state);
      if (values == null) {
        return 1.0d;
      }
      return remaining < values.length ? values[remaining] : 1.0d;
    }

    @Override
    void update(int state, int remaining, double value) {
      assert isExplored(state);

      if (isOne(value)) {
        assert isOne(upperBound(state, remaining));
        return;
      }

      int monotonicityUpdate;
      double[] values = stateBounds.get(state);
      if (values == null) {
        double[] newValues = new double[remaining + 1];
        Arrays.fill(newValues, value);
        monotonicityUpdate = 0;

        stateBounds.put(state, newValues);
      } else if (values.length <= remaining) {
        int oldLength = values.length;
        int newLength = Math.max(oldLength * 2, remaining + 1);

        values = Arrays.copyOf(values, newLength);
        Arrays.fill(values, oldLength, remaining + 1, value);
        Arrays.fill(values, remaining + 1, newLength, 1.0d);
        monotonicityUpdate = oldLength;

        stateBounds.put(state, values);
      } else {
        double oldValue = values[remaining];
        // Check monotonicity of added value
        assert lessOrEqual(value, oldValue) : "Updating " + oldValue + " to " + value;
        if (isEqual(value, oldValue)) {
          return;
        }
        values[remaining] = value;
        monotonicityUpdate = remaining;
      }

      for (int i = 0; i < monotonicityUpdate; i++) {
        if (values[i] > value) {
          values[i] = value;
        }
      }
    }

    @Override
    public boolean storesExact() {
      return true;
    }

    @Override
    public boolean stores(int remaining) {
      return true;
    }

    @Override
    public String toString() {
      return String.format("DenseValues(%s)@%g", heuristic, precision);
    }
  }
}
