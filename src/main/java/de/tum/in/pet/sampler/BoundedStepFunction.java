package de.tum.in.pet.sampler;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import java.util.List;

@FunctionalInterface
public interface BoundedStepFunction {
  Bounds step(int state, int remainingSteps, List<Distribution> choices, Bounds[] successorBounds);
}
