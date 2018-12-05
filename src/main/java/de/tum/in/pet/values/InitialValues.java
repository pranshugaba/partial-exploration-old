package de.tum.in.pet.values;

import prism.PrismException;

@FunctionalInterface
public interface InitialValues<S> {
  Bounds initialValues(S state) throws PrismException;
}
