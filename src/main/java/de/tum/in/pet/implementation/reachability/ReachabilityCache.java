package de.tum.in.pet.implementation.reachability;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.IntPredicate;

public class ReachabilityCache implements IntPredicate {
  enum Type {
    TARGET, NON_TARGET
  }

  private final Int2ObjectMap<Type> states = new Int2ObjectOpenHashMap<>();
  private final IntPredicate target;

  public ReachabilityCache(IntPredicate target) {
    this.target = target;
  }

  private Type type(int state) {
    assert !states.containsKey(state);
    return target.test(state) ? Type.TARGET : Type.NON_TARGET;
  }

  @Override
  public boolean test(int state) {
    return states.computeIfAbsent(state, this::type) == Type.TARGET;
  }
}
