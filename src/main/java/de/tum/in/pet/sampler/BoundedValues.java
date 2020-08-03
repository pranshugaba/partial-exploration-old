package de.tum.in.pet.sampler;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import java.util.List;

public interface BoundedValues {
  Bounds bounds(int state, int remaining);

  boolean isSolved(int state, int remaining);

  int sampleNextState(int state, int remaining, List<Distribution> choices);

  void update(int state, int remaining, List<Distribution> choices);

  void update(int state, int remaining, Bounds bounds);

  void explored(int state, int remaining);

  boolean storesExact();

  boolean stores(int remaining);
}
