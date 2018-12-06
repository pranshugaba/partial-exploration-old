package de.tum.in.pet.implementation.reachability;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public class StateToIntTarget<S> implements IntPredicate {
  private final Predicate<S> target;
  private final IntFunction<S> translation;

  public StateToIntTarget(Predicate<S> target, IntFunction<S> translation) {
    this.target = target;
    this.translation = translation;
  }

  @Override
  public boolean test(int value) {
    return target.test(translation.apply(value));
  }

  @Override
  public String toString() {
    return target.toString();
  }
}
