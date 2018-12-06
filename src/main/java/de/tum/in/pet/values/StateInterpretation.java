package de.tum.in.pet.values;

@FunctionalInterface
public interface StateInterpretation<R> {
  R interpret(Bounds bounds);
}
