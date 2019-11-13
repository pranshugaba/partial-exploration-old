package de.tum.in.pet.values.unbounded;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.List;
import prism.PrismException;

public interface StateUpdate {
  Bounds update(int state, List<Distribution> choices, StateValueFunction values)
      throws PrismException;

  Bounds updateCollapsed(int state, List<Distribution> choices, IntCollection collapsedStates,
      StateValueFunction values) throws PrismException;

  boolean isSmallestFixPoint();
}
