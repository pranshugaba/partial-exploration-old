package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.util.Util;
import explicit.DTMC;
import explicit.DTMCSimple;
import explicit.Distribution;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import prism.ModelGenerator;
import prism.PrismException;

public class CollapsingDTMCExplorer extends AbstractCollapsingExplorer<DTMC>
    implements CollapsingExplorer.CollapsingDTMCExplorer {
  private final DTMCExplorer partialExplorer;
  private final DTMCSimple collapsedModel = new DTMCSimple();

  public CollapsingDTMCExplorer(ModelGenerator generator) throws PrismException {
    partialExplorer = new DefaultDTMCExplorer(generator, this::addState);

    for (int initialState : partialExplorer.getModel().getInitialStates()) {
      exploreState(initialState);
      collapsedModel.addInitialState(initialState);
    }
  }

  @Override
  public DTMCExplorer getPartialExplorer() {
    return partialExplorer;
  }

  @Override
  public DTMC getModel() {
    return collapsedModel;
  }

  @Override
  public Distribution getDistribution(int state) {
    assert isStateExplored(state);
    return collapsedModel.getTransitions(state);
  }

  private void addState(int state) {
    int addedState = collapsedModel.addState();
    assert addedState == state;
    stateCollapse.addState(state);
  }

  @Override
  public IntList collapse(List<? extends IntSet> stateList) {
    if (stateList.isEmpty()) {
      return IntLists.EMPTY_LIST;
    }

    NatBitSet collapsedStates = NatBitSets.set();

    // Collapse the states
    IntList representatives = new IntArrayList(stateList.size());
    for (IntSet states : stateList) {
      collapsedStates.or(states);
      int representativeState = this.stateCollapse.collapse(states);

      states.forEach((IntConsumer) collapsedModel::clearState);
      collapsedModel.addDeadlockState(representativeState);
      representatives.add(representativeState);
    }

    stateCollapse.getExploredStates().forEach((int state) -> {
      assert state < collapsedModel.getNumStates();
      Distribution distribution = collapsedModel.getTransitions(state);
      if (Util.containsOneOf(distribution, collapsedStates)) {
        collapsedModel.clearState(state);
        distribution.forEach(entry -> collapsedModel.addToProbability(state,
            stateCollapse.getCollapsedRepresentative(entry.getKey()), entry.getValue()));
      } else {
        assert Objects.equals(distribution,
            Util.map(distribution, stateCollapse::getCollapsedRepresentative));
      }
    });

    collapsedModel.clearInitialStates();
    getPartialExplorer().getInitialStates()
        .forEach((int initialState) -> collapsedModel
            .addInitialState(stateCollapse.getCollapsedRepresentative(initialState)));

    // All explored states are merged with an explored state
    assert getPartialExplorer().getExploredStates().intStream()
        .map(stateCollapse::getCollapsedRepresentative).allMatch(stateCollapse::isExploredState);
    // All distributions in the collapsed model only point to a root in the UF
    assert IntStream.range(0, collapsedModel.getNumStates())
        .mapToObj(collapsedModel::getTransitions)
        .filter(Objects::nonNull)
        .map(Distribution::getSupport)
        .flatMap(Set::stream)
        .mapToInt(Integer::intValue)
        .allMatch(i -> stateCollapse.getCollapsedRepresentative(i) == i);

    return representatives;
  }

  @Override
  public void exploreState(int number) throws PrismException {
    if (isStateExplored(number)) {
      return;
    }

    partialExplorer.exploreState(number);
    stateCollapse.exploreState(number);

    DTMC partialModel = partialExplorer.getModel();

    // Collect all transitions which are not self loops.
    partialModel.forEachTransition(number, (source, target, probability) -> {
      int dest = stateCollapse.getCollapsedRepresentative(target);
      if (dest == number) {
        return;
      }
      collapsedModel.addToProbability(number, dest, probability);
    });

    if (collapsedModel.getTransitions(number).isEmpty()) {
      // Only self-loops
      collapsedModel.addDeadlockState(number);
    }
  }
}
