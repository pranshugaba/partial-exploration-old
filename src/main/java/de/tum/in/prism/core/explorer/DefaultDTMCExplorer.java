package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.util.Util;
import explicit.DTMCExplicit;
import explicit.DTMCSimple;
import explicit.Distribution;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.function.IntConsumer;
import parser.State;
import prism.ModelGenerator;
import prism.PrismComponent;
import prism.PrismException;

public class DefaultDTMCExplorer extends PrismComponent implements Explorer.DTMCExplorer {
  private final DTMCSimple partialModel = new DTMCSimple();
  private final ModelGenerator generator;
  private final IntConsumer addedStateConsumer;
  private final StateToIndex stateMap = new StateToIndex();
  // All states which are in the partial model and explored
  private final NatBitSet exploredStates = NatBitSets.set();

  public DefaultDTMCExplorer(ModelGenerator generator, IntConsumer addedStateConsumer)
      throws PrismException {
    this.generator = generator;
    this.addedStateConsumer = addedStateConsumer;

    for (State initialState : generator.getInitialStates()) {
      int stateNumber = addState(initialState);
      exploreState(stateNumber);
      partialModel.addInitialState(stateNumber);
    }
  }

  public DefaultDTMCExplorer(ModelGenerator generator)
      throws PrismException {
    this(generator, s -> {});
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
  public DTMCExplicit getModel() {
    return partialModel;
  }

  @Override
  public Distribution getDistribution(int state) {
    assert isStateExplored(state);
    return partialModel.getTransitions(state);
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

    int transitionCount = generator.getNumTransitions();

    Distribution distribution = new Distribution();
    for (int transition = 0; transition < transitionCount; transition++) {
      double transitionProbability = generator.getTransitionProbability(0, transition);
      State transitionTarget = generator.computeTransitionTarget(0, transition);
      int transitionTargetNumber = addState(transitionTarget);

      assert transitionProbability > 0;
      distribution.add(transitionTargetNumber, transitionProbability);
    }
    distribution = Util.scale(distribution);
    partialModel.trans.set(stateNumber, distribution);
    if (distribution.isEmpty()) {
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
  public IntIterable getInitialStates() {
    return () -> IntIterators.asIntIterator(partialModel.getInitialStates().iterator());
  }

  @Override
  public int exploredStateCount() {
    return exploredStates.size();
  }

  @Override
  public State getState(int stateNumber) {
    return stateMap.getState(stateNumber);
  }
}
