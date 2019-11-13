package de.tum.in.pet.values.bounded;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import java.util.List;

@FunctionalInterface
public interface StateUpdateBounded {
  Bounds update(int state, int remainingSteps, List<Distribution> choices,
      StateValuesBoundedFunction values);
}
