package de.tum.in.prism.core.builder;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.bounds.StateUpdateUnbounded;
import de.tum.in.prism.core.explorer.CollapseView;
import de.tum.in.prism.core.explorer.Explorer;
import de.tum.in.prism.core.util.SuccessorHeuristic;
import de.tum.in.prism.core.util.Util;
import de.tum.in.prism.util.DTMC;
import de.tum.in.prism.util.Distribution;
import de.tum.in.prism.util.ECComputer;
import de.tum.in.prism.util.MDP;
import de.tum.in.prism.util.MEC;
import de.tum.in.prism.util.Model;
import explicit.SCCComputer;
import explicit.SCCConsumerStore;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;
import prism.PrismComponent;
import prism.PrismException;

public abstract class CoreUnboundedSamplingBuilder<M extends Model> {
  private static final Logger logger =
      Logger.getLogger(CoreUnboundedSamplingBuilder.class.getName());

  private static final long MAX_EXPLORES_PER_SAMPLE = 10;
  private static final long MAX_BACK_TRACE_PER_SAMPLE = 5;
  private static final long REPORT_PROGRESS_EVERY_STEPS = 5000000;

  protected final PrismComponent prism;
  protected final Explorer<M> explorer;
  protected final CollapseView<M> collapseView;

  private final SuccessorHeuristic heuristic;
  private final StateUpdateUnbounded stateUpdate;

  protected boolean newStatesSinceCollapse = true;
  private double precision = 1;
  private long collapseThreshold = 10;
  private long loopCount = 0;

  private long sampleSteps = 0;
  private long samples = 0;
  private long backtraceCount = 0;
  private long backtraceSteps = 0;

  protected CoreUnboundedSamplingBuilder(PrismComponent prism, Explorer<M> explorer,
      StateUpdateUnbounded stateUpdate, SuccessorHeuristic heuristic) {
    this.prism = prism;
    this.explorer = explorer;
    this.stateUpdate = stateUpdate;
    this.heuristic = heuristic;
    this.collapseView = new CollapseView<>(explorer.model());
  }

  public Explorer<M> getExplorer() {
    return explorer;
  }

  @SuppressWarnings("UseOfClone")
  public AnnotatedModel<M> build(double precision) throws PrismException {
    stateUpdate.setPrecision(precision);
    if (this.precision < precision) {
      assert StreamSupport.stream(explorer.initialStates().spliterator(), false)
          .mapToInt(collapseView::representative)
          .allMatch(representative -> stateUpdate.getUpperBound(representative) < precision);
      logger.log(Level.FINE, "Returning already computed model of precision {0} (requested: {1})",
          new Object[] {this.precision, precision});
      return new AnnotatedModel<>(explorer.model(), explorer::getState,
          explorer.exploredStates().clone());
    }
    logger.log(Level.INFO, "Learning the infinite core for precision {0}", precision);
    this.precision = precision;

    long timer = System.nanoTime();

    IntCollection initialStates = new IntArrayList();
    explorer.initialStates().forEach((IntConsumer) initialStates::add);
    for (int initialState : initialStates) {
      // The representative of the initial states might be a different state
      int representative = collapseView.representative(initialState);
      while (stateUpdate.getUpperBound(representative) >= this.precision) {
        if (sample(representative)) {
          // If MECs have been merged, update the representative (it might have changed)
          representative = collapseView.representative(initialState);
          assert collapseView.representative(representative)
              == collapseView.representative(initialState)
              && explorer.isExploredState(representative);
        }
      }
    }
    // TODO Is this needed?
    // collapse();

    long elapsedTime = System.nanoTime() - timer;

    logger.log(Level.INFO, () ->
        String.format("%n== Finished core building ==%n%s%n  Time: %f sec%n",
            getProgressString(), elapsedTime / (double) TimeUnit.SECONDS.toNanos(1)));

    return new AnnotatedModel<>(explorer.model(),
        explorer::getState, explorer.exploredStates().clone());
  }

  private boolean sample(int initialState) throws PrismException {
    assert !stateUpdate.isSolved(initialState);

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;
    IntSet visitedStateSet = new IntOpenHashSet();
    int currentState = initialState;
    int exploreCount = 0;
    int sampleBacktraceCount = 0;

    samples += 1;

    // Sample a path
    while (!visitedStateSet.contains(currentState)) {
      assert explorer.isExploredState(currentState) && !stateUpdate.isSolved(currentState);

      sampleSteps++;
      if (logger.isLoggable(Level.FINE) && sampleSteps % REPORT_PROGRESS_EVERY_STEPS == 0) {
        logger.log(Level.FINE, String.format("%n== Progress ==%n%s", getProgressString()));
      }

      visitStack.push(currentState);
      visitedStateSet.add(currentState);
      // Sample the successor
      int nextState = sampleNextState(currentState);

      if (nextState == -1 || currentState == nextState) {
        if (sampleBacktraceCount == MAX_BACK_TRACE_PER_SAMPLE) {
          break;
        }

        // We won't find anything of value if we continue to follow this path, backtrace until we
        // find an interesting state again
        // Note: We might as well completely restart the sampling here, but then we potentially
        // have to move to this interesting "fringe" region again
        do {
          backtraceSteps += 1;
          currentState = visitStack.popInt();
          visitedStateSet.remove(currentState);
        } while (update(currentState) < precision && currentState != initialState);
        if (currentState == initialState) {
          break;
        }

        backtraceCount += 1;
        sampleBacktraceCount += 1;
      } else {
        if (!explorer.isExploredState(nextState)) {
          if (exploreCount == MAX_EXPLORES_PER_SAMPLE) {
            // Explored along this path long enough
            break;
          }
          exploreCount += 1;
          explore(nextState);
        }
        currentState = nextState;
      }
    }

    // Handle end components
    if (visitedStates.contains(currentState)) {
      // Sampled the same state twice
      assert explorer.isExploredState(currentState);
      loopCount += 1;
      // We looped quite often - chances for this are high if there is a MEC, otherwise the
      // sampling probabilities would decrease
      if (loopCount > collapseThreshold) {
        // Search for fix points in the model
        boolean anythingChanged = handleComponents();

        loopCount = 0;
        // Some arbitrary increasing number - collapsing is expensive
        collapseThreshold = explorer.exploredStates().size();

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
      update(visitStack.popInt());
    }

    return false;
  }

  private int sampleNextState(int state) {
    assert explorer.isExploredState(state)
        && !stateUpdate.isSolved(state)
        && !collapseView.isRemoved(state);

    List<Distribution> choices = collapseView.getChoices(state);
    if (choices.isEmpty()) {
      update(state, choices);
      return -1;
    }

    return Util.sampleNextState(choices, heuristic,
        stateUpdate::getUpperBound,
        d -> stateUpdate.getUpperBound(state, d),
        s -> s == state || stateUpdate.getUpperBound(s) < precision);
  }

  private void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);
  }

  private boolean handleComponents() throws PrismException {
    if (!newStatesSinceCollapse) {
      return false;
    }
    newStatesSinceCollapse = false;
    List<NatBitSet> collapseStates = findCollapseStates();

    if (collapseStates.isEmpty()) {
      return false;
    }

    IntList representatives = collapseView.collapse(collapseStates);
    Iterator<NatBitSet> collapseIterator = collapseStates.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while (collapseIterator.hasNext()) {
      NatBitSet states = collapseIterator.next();
      int representative = representativeIterator.nextInt();

      assert explorer.isExploredState(representative);
      // Delete information of all collapsed states
      states.forEach((IntConsumer) stateUpdate::clear);
      // Update the representative
      update(representative);
    }

    return true;
  }

  private double update(int state) {
    return update(state, collapseView.getChoices(state));
  }

  private double update(int state, List<Distribution> choices) {
    assert explorer.isExploredState(state)
        && !stateUpdate.isSolved(state)
        && !collapseView.isRemoved(state);

    if (choices.isEmpty()) {
      stateUpdate.setZero(state);
      return 0.0d;
    }

    double maximalValue = 0d;
    for (Distribution distribution : choices) {
      double expectedValue = stateUpdate.getUpperBound(state, distribution);
      if (expectedValue > maximalValue) {
        maximalValue = expectedValue;
      }
    }
    stateUpdate.setUpperBound(state, maximalValue);
    return maximalValue;
  }

  protected abstract List<NatBitSet> findCollapseStates() throws PrismException;

  private String getProgressString() {
    Int2DoubleMap initialStateValues = new Int2DoubleAVLTreeMap();
    explorer.model().getInitialStates().forEach(initialState -> {
      int collapsedRepresentative = collapseView.representative(initialState);
      initialStateValues.put(initialState.intValue(),
          stateUpdate.getUpperBound(collapsedRepresentative));
    });

    return String.format("  Trials: %d, steps: %d, avg len: %f, backtrace count: %d, steps: %d%n"
            + "  States: %d/%d in partial/collapsed model (%d)%n"
            + "  Bounds: %s",
        samples, sampleSteps, (double) sampleSteps / (double) samples, backtraceCount,
        backtraceSteps, explorer.exploredStateCount(),
        explorer.exploredStateCount() - collapseView.getRemovedStates().size(),
        explorer.fringeStateCount(), initialStateValues);
  }

  public static class MDPBuilder extends CoreUnboundedSamplingBuilder<MDP> {
    private final NatBitSet statesInMec = NatBitSets.set();

    public MDPBuilder(PrismComponent prism, Explorer<MDP> explorer,
        StateUpdateUnbounded stateUpdate, SuccessorHeuristic heuristic) {
      super(prism, explorer, stateUpdate, heuristic);
    }

    @Override
    protected List<NatBitSet> findCollapseStates() {
      logger.log(Level.FINE, "\nStarting MECs collapse");

      NatBitSet exploredStates = explorer.exploredStates().clone();
      exploredStates.andNot(collapseView.getRemovedStates());

      List<MEC> mecs = ECComputer.computeMECs(collapseView, exploredStates);
      if (mecs.isEmpty()) {
        logger.log(Level.FINE, "Found no MECs");
        return Collections.emptyList();
      }

      Collection<NatBitSet> mecStates = new ArrayList<>(mecs.size());
      mecs.forEach(m -> mecStates.add(m.states));

      int statesInMecCount = statesInMec.size();
      List<NatBitSet> newMecStates = new ArrayList<>();
      for (NatBitSet mec : mecStates) {
        assert !mec.isEmpty();

        // Check if any of the states in this EC have not been touched yet - it is a new EC then.
        statesInMec.or(mec);
        int newSize = statesInMec.size();
        if (newSize > statesInMecCount) {
          statesInMecCount = newSize;
          newMecStates.add(mec);
        }
      }
      assert !newMecStates.isEmpty() : "No new EC states in " + mecStates;

      if (logger.isLoggable(Level.FINE)) {
        int mecStateCount = newMecStates.stream().mapToInt(NatBitSet::size).sum();
        logger.log(Level.FINE, "Found {0} new MECs with {1} new states",
            new Object[] {newMecStates.size(), mecStateCount});
      }

      return newMecStates;
    }
  }

  public static class DTMCBuilder extends CoreUnboundedSamplingBuilder<DTMC> {
    private final NatBitSet statesInBscc = NatBitSets.set();

    public DTMCBuilder(PrismComponent prism, Explorer<DTMC> explorer,
        StateUpdateUnbounded stateUpdate, SuccessorHeuristic heuristic) {
      super(prism, explorer, stateUpdate, heuristic);
    }

    @Override
    protected List<NatBitSet> findCollapseStates() throws PrismException {
      logger.log(Level.FINE, "\nStarting BSCC collapse");

      SCCConsumerStore consumer = new SCCConsumerStore();
      SCCComputer sccComputer = SCCComputer.createSCCComputer(prism, collapseView, consumer);
      sccComputer.computeSCCs(true, collapseView::isRemoved);
      List<BitSet> bsccs = consumer.getBSCCs();

      if (bsccs.isEmpty()) {
        return Collections.emptyList();
      }

      int statesInBsccCount = statesInBscc.size();
      List<NatBitSet> newBsccStates = new ArrayList<>();
      for (BitSet bscc : bsccs) {
        NatBitSet states = NatBitSets.asSet(bscc);
        assert !states.isEmpty();

        // Check if any of the states in this MEC have not been touched yet - it is a new MEC then.
        statesInBscc.or(states);
        int newSize = statesInBscc.size();
        if (newSize > statesInBsccCount) {
          statesInBsccCount = newSize;
          newBsccStates.add(states);
        }
      }
      assert !newBsccStates.isEmpty() : bsccs;

      if (logger.isLoggable(Level.FINE)) {
        int bsccStateCount = newBsccStates.stream().mapToInt(NatBitSet::size).sum();
        logger.log(Level.FINE, "Found {0} new BSCCs with {1} new states",
            new Object[] {newBsccStates.size(), bsccStateCount});
      }

      return newBsccStates;
    }
  }
}
