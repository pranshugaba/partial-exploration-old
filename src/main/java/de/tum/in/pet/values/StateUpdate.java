package de.tum.in.pet.values;

import de.tum.in.pet.model.Distribution;
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
