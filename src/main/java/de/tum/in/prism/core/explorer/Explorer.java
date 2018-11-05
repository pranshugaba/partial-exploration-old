package de.tum.in.prism.core.explorer;

import com.google.common.collect.ImmutableList;
import de.tum.in.naturals.set.NatBitSet;
import explicit.DTMC;
import explicit.Distribution;
import explicit.Model;
import it.unimi.dsi.fastutil.ints.IntIterable;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public interface Explorer<M extends Model> {
  void exploreState(int stateNumber) throws PrismException;

  boolean isStateExplored(int number);

  IntIterable getInitialStates();

  int exploredStateCount();

  NatBitSet getExploredStates();

  M getModel();

  ModelGenerator getGenerator();

  State getState(int stateNumber);

  default int fringeStateCount() {
    return getModel().getNumStates() - exploredStateCount();
  }

  List<Distribution> getChoices(int state);

  interface MDPExplorer extends Explorer<explicit.MDP> {
    Distribution getChoice(int state, int action);
  }

  interface DTMCExplorer extends Explorer<DTMC> {
    @Override
    default List<Distribution> getChoices(int state) {
      return ImmutableList.of(getDistribution(state));
    }

    Distribution getDistribution(int state);
  }
}
