package de.tum.in.pet.values;

@FunctionalInterface
public interface StateInterpretation {
  Object interpret(int state, Bounds bounds);
}
