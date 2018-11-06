package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.prism.util.Distribution;
import explicit.Model;
import it.unimi.dsi.fastutil.ints.IntIterable;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public interface Explorer<M extends Model> {
  void exploreState(int state) throws PrismException;

  boolean isExploredState(int state);

  boolean isTargetState(int state);


  IntIterable initialStates();

  int exploredStateCount();

  NatBitSet exploredStates();

  M model();

  ModelGenerator generator();

  State getState(int state);

  default int fringeStateCount() {
    return model().getNumStates() - exploredStateCount();
  }

  List<Distribution> getChoices(int state);
}
