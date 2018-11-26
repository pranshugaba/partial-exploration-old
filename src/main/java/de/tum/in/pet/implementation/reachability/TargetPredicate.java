package de.tum.in.pet.implementation.reachability;

import parser.State;
import prism.PrismException;

@FunctionalInterface
public interface TargetPredicate {
  boolean isTargetState(State state) throws PrismException;
}
