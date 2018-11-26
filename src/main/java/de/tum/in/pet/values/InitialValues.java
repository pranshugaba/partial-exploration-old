package de.tum.in.pet.values;

import parser.State;
import prism.PrismException;

@FunctionalInterface
public interface InitialValues {
  Bounds initialValues(State state) throws PrismException;
}
