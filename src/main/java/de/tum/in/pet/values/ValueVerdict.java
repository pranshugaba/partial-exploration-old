package de.tum.in.pet.values;

@FunctionalInterface
public interface ValueVerdict {
  boolean isSolved(Bounds bounds);
}
