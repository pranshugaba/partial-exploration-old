package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import explicit.Model;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterators;
import parser.State;
import prism.ModelGenerator;

public abstract class AbstractCollapsingExplorer<M extends Model> implements CollapsingExplorer<M> {
  protected final StateCollapse stateCollapse = new StateCollapse();

  @Override
  public boolean isStateExplored(int number) {
    return stateCollapse.isExploredState(number);
  }

  @Override
  public IntIterable getInitialStates() {
    return () -> IntIterators.asIntIterator(getModel().getInitialStates().iterator());
  }

  @Override
  public int fringeStateCount() {
    return stateCollapse.fringeStateCount();
  }

  @Override
  public ModelGenerator getGenerator() {
    return getPartialExplorer().getGenerator();
  }

  @Override
  public State getState(int stateNumber) {
    return getPartialExplorer().getState(stateNumber);
  }

  @Override
  public NatBitSet getExploredStates() {
    return stateCollapse.getExploredStates();
  }

  @Override
  public StateCollapse getStateCollapse() {
    return stateCollapse;
  }

  @Override
  public int getCollapsedRepresentative(int stateNumber) {
    return stateCollapse.getCollapsedRepresentative(stateNumber);
  }

  @Override
  public int exploredStateCount() {
    return stateCollapse.exploredStatesCount();
  }

}
