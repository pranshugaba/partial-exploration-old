package de.tum.in.prism.core.explorer;

import explicit.DTMC;
import explicit.MDP;
import explicit.Model;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import prism.PrismException;

public interface CollapsingExplorer<M extends Model> extends Explorer<M> {
  Explorer<M> getPartialExplorer();

  IntList collapse(List<? extends IntSet> states) throws PrismException;

  int getCollapsedRepresentative(int stateNumber);

  default boolean isStateCollapsed(int stateNumber) {
    return getStateCollapse().isCollapsedState(stateNumber);
  }

  StateCollapse getStateCollapse();

  interface CollapsingMDPExplorer extends CollapsingExplorer<MDP>, MDPExplorer {

  }

  interface CollapsingDTMCExplorer extends CollapsingExplorer<DTMC>, Explorer.DTMCExplorer {

  }
}
