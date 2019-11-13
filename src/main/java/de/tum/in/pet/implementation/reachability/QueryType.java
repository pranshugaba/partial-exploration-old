package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.values.ValueInterpretation;
import de.tum.in.pet.values.ValueVerdict;
import de.tum.in.probmodels.util.annotation.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class QueryType<R> {
  public abstract ValueVerdict verdict();

  public abstract ValueUpdate update();

  public abstract ValueInterpretation<R> interpretation();


  public static <R> QueryType<R> of(ValueVerdict verdict, ValueUpdate update,
      ValueInterpretation<R> interpretation) {
    return QueryTypeTuple.create(verdict, update, interpretation);
  }

  @Override
  public String toString() {
    return verdict() + "/" + update();
  }
}
