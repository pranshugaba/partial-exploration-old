package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.bounded.StateValuesBounded;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.function.IntPredicate;

public class StateValuesBoundedReachability implements StateValuesBounded {
  private final Int2ObjectMap<Bounds[]> bounds = new Int2ObjectOpenHashMap<>();
  private final ReachabilityBounds reachability;

  public StateValuesBoundedReachability(IntPredicate target) {
    this.reachability = new ReachabilityBounds(target);
  }

  @Override
  public Bounds bounds(int state, int remainingSteps) {
    assert 0 <= remainingSteps;

    Bounds reachabilityBounds = reachability.get(state);
    if (reachabilityBounds != null) {
      return reachabilityBounds;
    }
    if (remainingSteps == 0) {
      // State is not a special reachability state
      return Bounds.ZERO_ZERO;
    }

    Bounds[] values = bounds.get(state);
    if (values == null) {
      return Bounds.ZERO_ONE;
    }
    return remainingSteps < values.length ? values[remainingSteps] : Bounds.ZERO_ONE;
  }

  @Override
  public void setBounds(int state, int remainingSteps, double lowerBound, double upperBound) {
    checkArgument(0 <= remainingSteps);

    Bounds bounds = Bounds.of(lowerBound, upperBound);
    Bounds[] values = this.bounds.get(state);

    if (values == null) {
      values = new Bounds[remainingSteps + 1];
      Arrays.fill(values, 0, remainingSteps + 1, bounds);
      this.bounds.put(state, values);
      return;
    }
    if (values.length <= remainingSteps) {
      int oldLength = values.length;
      int newLength = Math.max(oldLength * 2, remainingSteps + 1);
      values = Arrays.copyOf(values, newLength);
      Arrays.fill(values, oldLength, remainingSteps + 1, bounds);
      Arrays.fill(values, remainingSteps + 1, newLength, Bounds.ZERO_ONE);
      this.bounds.put(state, values);
      return;
    }
    values[remainingSteps] = bounds;

    // Maintain monotonicity
    /* for (int i = 0; i < remainingSteps; i++) {
      Bounds priorBounds = values[i];

      if (priorBounds.upperBound() > bounds.upperBound()) {
        values[i] = priorBounds.withUpper(bounds.upperBound());
      }
    } */
  }
}
