package de.tum.in.pet.generator;

import de.tum.in.pet.util.annotation.HashedTuple;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@HashedTuple
public abstract class ProductState<S, A> {
  // TODO Nicer than NULL
  @Nullable
  public abstract S system();

  public abstract A automaton();


  public static <S, A> ProductState<S, A> of(@Nullable S system, A automaton) {
    return ProductStateTuple.create(system, automaton);
  }


  @Override
  public String toString() {
    return system() + "x" + automaton();
  }
}
