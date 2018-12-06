package de.tum.in.pet.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.model.Action;
import de.tum.in.pet.model.Distribution;
import explicit.Model;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.List;
import prism.PrismException;

public interface Explorer<S, M extends Model> {
  IntCollection initialStates();

  S exploreState(int stateId) throws PrismException;

  boolean isExploredState(int stateId);

  List<Action> getActions(int stateId);

  List<Distribution> getChoices(int stateId);


  int exploredStateCount();

  NatBitSet exploredStates();

  M model();

  S getState(int stateId);

  int getStateId(S state);

  default int fringeStateCount() {
    return model().getNumStates() - exploredStateCount();
  }
}
