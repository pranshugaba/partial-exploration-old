package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.util.Util;
import explicit.Distribution;
import explicit.MDP;
import explicit.MDPSimple;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import prism.ModelGenerator;
import prism.PrismException;

public class CollapsingMDPExplorer extends AbstractCollapsingExplorer<MDP>
    implements CollapsingExplorer.CollapsingMDPExplorer {
  private static final Logger logger = Logger.getLogger(CollapsingMDPExplorer.class.getName());
  // TODO Sparse view on the partial model?
  // TODO Store the computed MECs directly?
  // TODO Predecessor-based optimizations (e.g. restricting the possible set of states
  // investigated by MEC search or update of distributions after collapse)

  private final MDPExplorer partialExplorer;
  private final MDPSimple collapsedModel = new MDPSimple();

  public CollapsingMDPExplorer(ModelGenerator generator) throws PrismException {
    partialExplorer = new DefaultMDPExplorer(generator, this::addState);

    for (int initialState : partialExplorer.getModel().getInitialStates()) {
      exploreState(initialState);
      collapsedModel.addInitialState(initialState);
    }
  }

  @Override
  public MDPExplorer getPartialExplorer() {
    return partialExplorer;
  }

  @Override
  public MDP getModel() {
    return collapsedModel;
  }

  @Override
  public Distribution getChoice(int state, int action) {
    assert isStateExplored(state);
    return collapsedModel.getChoice(state, action);
  }

  @Override
  public List<Distribution> getChoices(int state) {
    assert isStateExplored(state);
    List<Distribution> choices = collapsedModel.getChoices(state);
    if (choices == null) {
      return Collections.emptyList();
    }
    return choices;
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

      Collection<Distribution> distributions = new HashSet<>();
      states.forEach((int state) -> {
        // Delete transitions of all states in the MEC
        List<Distribution> stateDistributions = collapsedModel.getChoices(state);

        stateDistributions.forEach(distribution -> {
          // Build filtered distribution (only outgoing transitions)
          Distribution newDistribution = Util.map(distribution,
              successor -> states.contains(successor) ? -1 : successor);
          if (newDistribution.isEmpty()) {
            return;
          }
          distributions.add(Util.scale(distribution));
        });
        collapsedModel.clearState(state);
      });
      distributions.forEach(distribution ->
          collapsedModel.addChoice(representativeState, distribution));
      representatives.add(representativeState);
    }

    // Check that the collapsedModel transition map is consistent with the exploredStates bit set.
    assert IntStream.range(0, collapsedModel.getNumStates())
        .allMatch(i -> collapsedModel.getNumChoices(i) > 0 || stateCollapse.isCollapsedState(i));

    // Remap all transitions. Other states might be pointing to some now merged state - we have
    // to update them too.
    stateCollapse.getExploredStates().forEach((int state) -> {
      List<Distribution> transitions = collapsedModel.getChoices(state);
      for (int action = 0; action < transitions.size(); action++) {
        Distribution distribution = transitions.get(action);
        if (Util.containsOneOf(distribution, collapsedStates)) {
          transitions.set(action,
              Util.map(distribution, stateCollapse::getCollapsedRepresentative));
        } else {
          assert Objects.equals(distribution,
              Util.map(distribution, stateCollapse::getCollapsedRepresentative));
        }
      }
    });

    collapsedModel.clearInitialStates();
    getPartialExplorer().getInitialStates()
        .forEach((int initialState) -> collapsedModel
            .addInitialState(stateCollapse.getCollapsedRepresentative(initialState)));

    // All explored states are merged with an explored state
    assert getPartialExplorer().getExploredStates().intStream()
        .map(stateCollapse::getCollapsedRepresentative).allMatch(stateCollapse::isExploredState);

    if (logger.isLoggable(Level.INFO)) {
      int transitionCount = 0;
      int actionCount = 0;
      int maxTransitions = 0;
      int maxActions = 0;
      int count = representatives.size();
      for (int representative : representatives) {
        List<Distribution> distributions = collapsedModel.getChoices(representative);
        actionCount += distributions.size();
        maxActions = Math.max(maxActions, distributions.size());
        for (Distribution distribution : distributions) {
          transitionCount += distribution.size();
          maxTransitions = Math.max(maxTransitions, distribution.size());
        }
      }
      String infoString = String.format("Collapsed states: %d, Actions: %.2f avg/%d max, "
              + "Transitions %.2f avg/%d max", count, actionCount / (double) count, maxActions,
          transitionCount / (double) count, maxTransitions);
      logger.info(infoString);
    }

    return representatives;
  }

  @Override
  public void exploreState(int state) throws PrismException {
    if (isStateExplored(state)) {
      return;
    }
    stateCollapse.exploreState(state);
    partialExplorer.exploreState(state);

    MDP partialModel = partialExplorer.getModel();
    int numChoices = partialModel.getNumChoices(state);

    boolean hasPureSelfLoop = false;
    for (int action = 0; action < numChoices; action++) {
      Distribution distribution = new Distribution();
      // partialModel.getNumTransitions(state, action));

      // Collect all transitions which are not self loops.
      partialModel.forEachTransition(state, action, (source, target, probability) -> {
        int collapsedTarget = stateCollapse.getCollapsedRepresentative(target);
        if (collapsedTarget == state) {
          return;
        }
        distribution.add(collapsedTarget, probability);
      });

      if (distribution.isEmpty()) {
        hasPureSelfLoop = true;
        // Only self-loops, completely ignore this action
        continue;
      }

      collapsedModel.addChoice(state, Util.scale(distribution));
    }

    if (hasPureSelfLoop) {
      collapsedModel.addChoice(state, new Distribution(Map.of(state, 1.0d).entrySet().iterator()));
    }

    assert collapsedModel.getNumChoices(state) > 0;
  }
}
