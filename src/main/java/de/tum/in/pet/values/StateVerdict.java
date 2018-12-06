package de.tum.in.pet.values;

@FunctionalInterface
public interface StateVerdict {
  boolean isSolved(Bounds bounds);
}
