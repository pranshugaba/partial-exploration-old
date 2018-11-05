package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import explicit.Distribution;
import explicit.MDPExplicit;
import explicit.MDPSimple;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.List;
import java.util.function.IntConsumer;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public class DefaultMDPExplorer implements Explorer.MDPExplorer {
  private final MDPSimple partialModel = new MDPSimple();
  private final ModelGenerator generator;
  private final IntConsumer addedStateConsumer;
  private final StateToIndex stateMap = new StateToIndex();
  // All states which are in the partial model and explored
  private final NatBitSet exploredStates = NatBitSets.set();

  public DefaultMDPExplorer(ModelGenerator generator, IntConsumer addedStateConsumer)
      throws PrismException {
    this.generator = generator;
    this.addedStateConsumer = addedStateConsumer;

    for (State initialState : generator.getInitialStates()) {
      int stateNumber = addState(initialState);
      exploreState(stateNumber);
      partialModel.addInitialState(stateNumber);
    }
  }

  @Override
  public ModelGenerator getGenerator() {
    return generator;
  }

  @Override
  public NatBitSet getExploredStates() {
    return exploredStates;
  }

  @Override
  public MDPExplicit getModel() {
    return partialModel;
  }

  @Override
  public void exploreState(int stateNumber) throws PrismException {
    assert stateMap.check(stateNumber);
    if (isStateExplored(stateNumber)) {
      return;
    }
    exploredStates.set(stateNumber);

    State state = stateMap.getState(stateNumber);
    assert state != null;
    generator.exploreState(state);

    int actions = generator.getNumChoices();
    int totalTransitions = 0;
    for (int action = 0; action < actions; action++) {
      int transitionCount = generator.getNumTransitions(action);
      totalTransitions += transitionCount;

      Distribution distribution = new Distribution();
      for (int transition = 0; transition < transitionCount; transition++) {
        double transitionProbability = generator.getTransitionProbability(action, transition);
        State transitionTarget = generator.computeTransitionTarget(action, transition);
        int transitionTargetNumber = addState(transitionTarget);
        distribution.add(transitionTargetNumber, transitionProbability);
      }

      Object actionLabel = generator.getChoiceAction(action);
      int retVal = partialModel.addActionLabelledChoice(stateNumber, distribution, actionLabel);
      assert retVal != -1;
    }
    if (totalTransitions == 0) {
      partialModel.addDeadlockState(stateNumber);
    }
  }

  @Override
  public boolean isStateExplored(int number) {
    return exploredStates.contains(number);
  }

  private int addState(State state) {
    assert state != null;

    int stateNumber = stateMap.getStateNumber(state);
    if (stateNumber != -1) {
      return stateNumber;
    }

    int newStateNumber = partialModel.addState();
    assert newStateNumber == stateMap.size();
    stateMap.addState(state, newStateNumber);
    addedStateConsumer.accept(newStateNumber);
    return newStateNumber;
  }

  @Override
  public Distribution getChoice(int state, int action) {
    assert isStateExplored(state);
    return partialModel.getChoice(state, action);
  }

  @Override
  public IntIterable getInitialStates() {
    return () -> IntIterators.asIntIterator(partialModel.getInitialStates().iterator());
  }

  @Override
  public int exploredStateCount() {
    return exploredStates.size();
  }

  @Override
  public List<Distribution> getChoices(int state) {
    assert isStateExplored(state);
    return partialModel.getChoices(state);
  }

  @Override
  public State getState(int stateNumber) {
    return stateMap.getState(stateNumber);
  }
}
