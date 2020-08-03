package de.tum.in.pet.sampler;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.graph.ComponentAnalyser;
import de.tum.in.probmodels.graph.SccDecomposition;
import de.tum.in.probmodels.model.CollapseModel;
import de.tum.in.probmodels.model.CollapseView;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import prism.PrismException;

@SuppressWarnings("PMD.TooManyFields")
public class UnboundedSampler<S, M extends Model> implements Iterator<S, M> {
  private static final Logger logger = Logger.getLogger(UnboundedSampler.class.getName());

  private final Explorer<S, M> explorer;
  private final UnboundedValues values;
  private final CollapseModel<M> collapseModel;
  private final ComponentAnalyser analyser;

  private final NatBitSet statesInComponents = NatBitSets.set();
  private final IntSet sampledStates = new IntOpenHashSet();

  private long collapseThreshold;
  private int loopCount = 0;
  private boolean newStatesSinceCollapse = false;

  private final int maxBacktrackPerSample;
  private final int maxExploresPerSample;
  private final CollapseMethod collapseMethod;

  public UnboundedSampler(Explorer<S, M> explorer, ComponentAnalyser analyser,
      UnboundedValues values, UnboundedSamplerConfig config) {
    this.explorer = explorer;
    this.collapseModel = new CollapseView<>(explorer.model());
    this.analyser = analyser;
    this.values = values;
    this.collapseThreshold = config.initialCollapseThreshold();

    maxBacktrackPerSample = config.maxBacktrackPerSample();
    maxExploresPerSample = config.maxExploresPerSample();
    collapseMethod = config.collapseMethod();
  }

  @Override
  public Explorer<S, M> explorer() {
    return explorer;
  }

  @Override
  public AnnotatedModel<M> model() {
    IntSet exploredStates = new IntOpenHashSet(explorer.exploredStates());
    exploredStates.removeIf((int state) -> values.isUnknown(collapseModel.representative(state)));
    return new AnnotatedModel<>(explorer.model(), explorer::getState, exploredStates);
  }

  @Override
  public Bounds bounds(int state) {
    return values.bounds(state);
  }

  @Override
  public void run() throws PrismException {
    long count = 0;
    for (int initialState : explorer.initialStates()) {
      // The representative of the initial states might be a different state
      int representative = collapseModel.representative(initialState);
      while (!values.isSolved(representative)) {
        count += 1;
        if (sample(representative)) {
          // If MECs have been merged, update the representative (it might have changed)
          representative = collapseModel.representative(initialState);
          assert explorer.isExploredState(representative)
              && collapseModel.representative(representative)
              == collapseModel.representative(initialState);
        }
      }
    }
  }

  private boolean sample(int initialState) throws PrismException {
    assert !values.isSolved(initialState);

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;
    IntSet visitedStateSet = new IntOpenHashSet();

    int exploreCount = 0;
    int currentState = initialState;
    int sampleBacktraceCount = 0;
    int stateRevisit = 0;
    while (stateRevisit < 10) {
      assert explorer.isExploredState(currentState);

      visitStack.push(currentState);
      if (visitedStateSet.add(currentState)) {
        sampledStates.add(currentState);
      } else {
        stateRevisit += 1;
      }

      // Sample the successor
      int nextState = values.sampleNextState(currentState, choices(currentState));

      if (nextState == -1 || currentState == nextState) {
        if (sampleBacktraceCount == maxBacktrackPerSample) {
          break;
        }

        // We won't find anything of value if we continue to follow this path, backtrace until we
        // find an interesting state again
        // Note: We might as well completely restart the sampling here, but then we potentially
        // have to move to this interesting "fringe" region again
        do {
          currentState = visitStack.popInt();
          visitedStateSet.remove(currentState);
          values.update(currentState, choices(currentState));
        } while (values.isSolved(currentState) && currentState != initialState);
        if (currentState == initialState) {
          break;
        }
        assert !values.isSolved(currentState);

        sampleBacktraceCount += 1;
      } else {
        if (!explorer.isExploredState(nextState)) {
          if (exploreCount == maxExploresPerSample) {
            break;
          }
          exploreCount += 1;
          explore(nextState);
        }

        currentState = nextState;
      }
    }

    // Handle end components
    if (stateRevisit > 5) {
      loopCount += 1;
      // We looped quite often - chances for this are high if there is a MEC, otherwise the
      // sampling probabilities would decrease
      if (loopCount > collapseThreshold) {
        // Search for fix points in the model
        // TODO Only search on frequently visited states?
        boolean anythingChanged = handleComponents();

        loopCount = 0;
        // Some arbitrary increasing number - collapsing is expensive
        collapseThreshold += explorer.exploredStates().size();

        if (anythingChanged) {
          // The path could now contain invalid states (i.e. some which have been merged), we have
          // to sample again.
          visitedStates.clear();

          return true;
        }
      }
    }

    // Propagate values backwards along the path
    while (!visitStack.isEmpty()) {
      int state = visitStack.popInt();
      values.update(state, choices(state));
    }

    return false;
  }

  private void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);
    values.explored(state);
  }

  private boolean handleComponents() {
    if (!newStatesSinceCollapse) {
      return false;
    }
    newStatesSinceCollapse = false;

    logger.log(Level.INFO, "Searching components");

    NatBitSet states;
    if (collapseMethod == CollapseMethod.ALL_STATES) {
      states = NatBitSets.copyOf(explorer.exploredStates());
      states.removeAll(collapseModel.removedStates());
    } else {
      states = NatBitSets.copyOf(sampledStates);
    }
    assert states.stream().noneMatch(collapseModel::isRemoved);
    assert explorer.exploredStates().containsAll(states);

    List<NatBitSet> components = analyser.findComponents(collapseModel, states);
    sampledStates.clear();

    if (components.isEmpty()) {
      return false;
    }

    int statesInComponentsCount = statesInComponents.size();
    List<NatBitSet> newComponents = new ArrayList<>();
    for (NatBitSet component : components) {
      assert !component.isEmpty();

      // Check if any of the states in this component have not been touched yet - then it is new
      statesInComponents.or(component);
      int newSize = statesInComponents.size();
      if (newSize > statesInComponentsCount) {
        statesInComponentsCount = newSize;
        newComponents.add(component);
      }
    }

    if (values.isSmallestFixPoint()) {
      // TODO Hack - better?
      Predicate<NatBitSet> isNotBottom = component ->
          !SccDecomposition.isBscc(collapseModel::getSuccessors, component);
      newComponents.removeIf(isNotBottom);

      logger.log(Level.FINER, "Bottom components: {0}", newComponents);
      if (newComponents.isEmpty()) {
        return false;
      }
    } else {
      assert !newComponents.isEmpty();
    }

    if (logger.isLoggable(Level.FINE)) {
      int count = newComponents.stream().mapToInt(NatBitSet::size).sum();
      logger.log(Level.FINE, "Found {0} new components with {1} new states",
          new Object[] {newComponents.size(), count});
    }

    IntList representatives = collapseModel.collapse(newComponents);
    var collapseIterator = newComponents.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while (collapseIterator.hasNext()) {
      int representative = representativeIterator.nextInt();
      values.collapse(representative, choices(representative), collapseIterator.next());
    }

    return true;
  }

  private List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    return collapseModel.getChoices(state);
  }
}
