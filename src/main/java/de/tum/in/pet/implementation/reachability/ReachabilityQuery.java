package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.util.annotation.Tuple;
import de.tum.in.pet.values.StateInterpretation;
import de.tum.in.pet.values.StateVerdict;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class ReachabilityQuery {
  public abstract TargetPredicate predicate();

  public abstract ValueUpdateType updateType();

  public abstract StateVerdict verdict();

  public abstract StateInterpretation interpretation();


  @Override
  public String toString() {
    return predicate() + " [" + updateType() + "/" + verdict() + "]";
  }
}
