package de.tum.in.pet.generator;

import java.util.Collection;
import prism.PrismException;

public interface Generator<S> {
  Collection<S> initialStates() throws PrismException;

  Collection<Choice<S>> choices(S state) throws PrismException;
}
