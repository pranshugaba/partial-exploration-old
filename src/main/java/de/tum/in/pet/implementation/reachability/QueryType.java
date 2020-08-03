package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.values.ValueVerdict;
import de.tum.in.probmodels.util.annotation.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class QueryType<R> {
  public abstract ValueVerdict<R> verdict();

  public abstract ValueUpdate update();

  public static <R> QueryType<R> of(ValueVerdict<R> verdict, ValueUpdate update) {
    return QueryTypeTuple.create(verdict, update);
  }

  @Override
  public String toString() {
    return verdict() + "/" + update();
  }
}
