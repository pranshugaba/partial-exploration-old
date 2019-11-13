package de.tum.in.pet.implementation.reachability;

import de.tum.in.probmodels.generator.ProductState;
import java.util.function.Predicate;

public class UntilTargetPredicate<S> implements Predicate<ProductState<S, Boolean>> {
  private final Predicate<S> target;

  public UntilTargetPredicate(Predicate<S> target) {
    this.target = target;
  }

  @Override
  public boolean test(ProductState<S, Boolean> state) {
    return state.automaton() && target.test(state.system());
  }

  @Override
  public String toString() {
    return String.format("UNTIL[%s]", target);
  }
}
