package de.tum.in.pet.values;

@FunctionalInterface
public interface ValueInterpretation<R> {
  R interpret(Bounds bounds);
}
