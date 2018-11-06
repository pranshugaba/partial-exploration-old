package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.util.Action;
import de.tum.in.prism.util.Distribution;
import de.tum.in.prism.util.Model;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public abstract class AbstractExplorer<M extends Model> implements Explorer<M> {
  private final ModelGenerator generator;
  private final StateToIndex stateMap = new StateToIndex();
  // All states which are in the partial model and explored
  private final NatBitSet exploredStates = NatBitSets.set();
  private final M model;

  protected AbstractExplorer(ModelGenerator generator, M model) throws PrismException {
    this.generator = generator;
    this.model = model;

    IntList initialStates = new IntArrayList();
    for (State initialState : generator.getInitialStates()) {
      int stateNumber = addState(initialState);
      exploreState(stateNumber);
      initialStates.add(stateNumber);
    }
    model.setInitialStates(initialStates);
  }

  @Override
  public ModelGenerator generator() {
    return generator;
  }

  @Override
  public NatBitSet exploredStates() {
    return exploredStates;
  }

  @Override
  public M model() {
    return model;
  }

  @Override
  public void exploreState(int stateNumber) throws PrismException {
    assert stateMap.check(stateNumber);
    if (isExploredState(stateNumber)) {
      return;
    }
    exploredStates.set(stateNumber);

    State state = stateMap.getState(stateNumber);
    assert state != null;
    generator.exploreState(state);
    getActionsOfExploredState(stateNumber).forEach(action -> model.addChoice(stateNumber, action));
  }

  protected abstract List<Action> getActionsOfExploredState(int stateNumber) throws PrismException;

  @Override
  public boolean isExploredState(int number) {
    return exploredStates.contains(number);
  }

  protected int addState(State state) {
    assert state != null;

    int stateNumber = stateMap.getStateNumber(state);
    if (stateNumber != -1) {
      return stateNumber;
    }

    int newStateNumber = model.addState();
    assert newStateNumber == stateMap.size();
    stateMap.addState(state, newStateNumber);
    return newStateNumber;
  }

  @Override
  public IntIterable initialStates() {
    return () -> IntIterators.asIntIterator(model.getInitialStates().iterator());
  }

  @Override
  public int exploredStateCount() {
    return exploredStates.size();
  }

  @Override
  public List<Distribution> getChoices(int state) {
    assert isExploredState(state);
    return model.getChoices(state);
  }

  @Override
  public State getState(int stateNumber) {
    return stateMap.getState(stateNumber);
  }

  @Override
  public boolean isTargetState(int state) {
    return false;
  }
}
