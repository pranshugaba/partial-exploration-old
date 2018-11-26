package de.tum.in.pet.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.model.Distribution;
import explicit.Model;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public interface Explorer<M extends Model> {
  IntCollection initialStates();

  State exploreState(int state) throws PrismException;

  boolean isExploredState(int state);

  List<Distribution> getChoices(int state);


  int exploredStateCount();

  NatBitSet exploredStates();

  M model();

  ModelGenerator generator();

  State getState(int state);

  default int fringeStateCount() {
    return model().getNumStates() - exploredStateCount();
  }
}
