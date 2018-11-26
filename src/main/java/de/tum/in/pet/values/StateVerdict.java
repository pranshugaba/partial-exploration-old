package de.tum.in.pet.values;

@FunctionalInterface
public interface StateVerdict {
  boolean isSolved(int state, Bounds bounds);
}
