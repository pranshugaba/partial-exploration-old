package de.tum.in.prism.core.builder;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.bounds.StateUpdate;
import de.tum.in.prism.core.explorer.CollapsingExplorer;
import de.tum.in.prism.core.util.ECComputerFast;
import de.tum.in.prism.core.util.MEC;
import de.tum.in.prism.core.util.SuccessorHeuristic;
import de.tum.in.prism.core.util.Util;
import explicit.DTMC;
import explicit.Distribution;
import explicit.MDP;
import explicit.Model;
import explicit.SCCComputer;
import explicit.SCCConsumerStore;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.ArrayList;
import java.util.BitSet;
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
import prism.PrismUtils;

public abstract class UnboundedSamplingBuilder<M extends Model> {
  private static final Logger logger = Logger.getLogger(UnboundedSamplingBuilder.class.getName());

  private static final int MAX_EXPLORES_PER_SAMPLE = 10;
  private static final int MAX_BACK_TRACE_PER_SAMPLE = 5;
  private static final int REPORT_PROGRESS_EVERY_STEPS = 50000;

  protected final PrismComponent prism;

  private final CollapsingExplorer<M> explorer;
  private final SuccessorHeuristic heuristic;
  protected boolean newStatesSinceCollapse = true;
  private double precision = 1;

  private int collapseThreshold = 10;
  private int loopCount = 0;
  private StateUpdate stateUpdate;

  private int sampleSteps = 0;
  private int samples = 0;
  private int backtraceCount = 0;
  private int backtraceSteps = 0;

  public UnboundedSamplingBuilder(PrismComponent prism, CollapsingExplorer<M> explorer,
      StateUpdate stateUpdate) {
    this.prism = prism;
    this.explorer = explorer;
    this.stateUpdate = stateUpdate;
    heuristic = SuccessorHeuristic.GUIDED;
  }

  public CollapsingExplorer<M> getExplorer() {
    return explorer;
  }

  @SuppressWarnings("UseOfClone")
  public AnnotatedModel<M> build(double precision) throws PrismException {
    stateUpdate.setPrecision(precision);
    if (this.precision < precision) {
      assert StreamSupport.stream(explorer.getInitialStates().spliterator(), false)
          .mapToInt(explorer::getCollapsedRepresentative)
          .distinct()
          .allMatch(representative -> stateUpdate.getDifference(representative) < precision);
      logger.log(Level.FINE, "Returning already computed model of precision {0} (requested: {1})",
          new Object[] {this.precision, precision});
      return new AnnotatedModel<>(explorer.getModel(), explorer::getState,
          explorer.getExploredStates().clone());
    }
    logger.log(Level.INFO, "Learning the infinite core for precision {0}", precision);
    this.precision = precision;

    long timer = System.nanoTime();

    IntCollection initialStates = new IntArrayList();
    explorer.getInitialStates().forEach((IntConsumer) initialStates::add);
    for (int initialState : initialStates) {
      // The representative of the initial states might be a different state
      int representative = explorer.getCollapsedRepresentative(initialState);
      while (stateUpdate.getDifference(representative) > this.precision) {
        if (sample(representative)) {
          // If MECs have been merged, update the representative (it might have changed)
          representative = explorer.getCollapsedRepresentative(initialState);
          assert explorer.getCollapsedRepresentative(representative) ==
              explorer.getCollapsedRepresentative(initialState)
              && explorer.isStateExplored(representative);
        }
      }
    }
    // TODO Is this needed?
    collapse();

    long elapsedTime = System.nanoTime() - timer;

    logger.log(Level.INFO, () ->
        String.format("%n== Finished core building ==%n%s%n  Time: %f sec%n",
            getProgressString(), elapsedTime / (double) TimeUnit.SECONDS.toNanos(1)));

    return new AnnotatedModel<>(explorer.getPartialExplorer().getModel(),
        explorer::getState, explorer.getPartialExplorer().getExploredStates().clone());
  }

  private boolean sample(int initialState) throws PrismException {
    assert !stateUpdate.isSolved(initialState);

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;
    int currentState = initialState;
    int exploreCount = 0;
    int sampleBacktraceCount = 0;

    samples += 1;

    // Sample a path
    while (!visitedStates.contains(currentState)) {
      assert explorer.isStateExplored(currentState);
      assert !stateUpdate.isSolved(currentState);

      sampleSteps++;
      if (logger.isLoggable(Level.FINE) && sampleSteps % REPORT_PROGRESS_EVERY_STEPS == 0) {
        logger.log(Level.FINE, String.format("%n== Progress ==%n%s", getProgressString()));
      }

      visitStack.push(currentState);
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
        } while (update(currentState) < precision && currentState != initialState);
        if (currentState == initialState) {
          break;
        }

        backtraceCount += 1;
        sampleBacktraceCount += 1;
      } else {
        if (!explorer.isStateExplored(nextState)) {
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

    // Collapse states
    if (visitedStates.contains(currentState)) {
      // Sampled the same state twice
      assert explorer.isStateExplored(currentState);
      loopCount += 1;
      // We looped quite often - chances for this are high if there is a MEC, otherwise the
      // sampling probabilities would decrease
      if (loopCount > collapseThreshold) {
        // Search for MECs in the partial MDP and process the MECs which were not processed yet
        collapse();

        loopCount = 0;
        collapseThreshold = explorer.exploredStateCount();

        // The path could now contain invalid states (i.e. some which have been merged), we have
        // to sample again.
        visitedStates.clear();
        return true;
      }
    }

    // Propagate values backwards along the path
    while (!visitStack.isEmpty()) {
      update(visitStack.popInt());
    }

    return false;
  }

  private int sampleNextState(int state) {
    assert explorer.isStateExplored(state) && !stateUpdate.isSolved(state);

    List<Distribution> choices = explorer.getChoices(state);
    if (choices.isEmpty()) {
      stateUpdate.update(state, choices);
      return -1;
    }

    int choiceCount = choices.size();
    Distribution distribution;

    if (choiceCount == 1) {
      distribution = choices.get(0);
      if (stateUpdate.getExpectedDifference(distribution) < precision) {
        return -1;
      }
    } else {
      int maximalBestActions = 0;
      double bestValue = 0d;
      double[] actionUpperBounds = new double[choiceCount];
      for (int choice = 0; choice < choiceCount; choice++) {
        double upperBound = stateUpdate.getExpectedUpperBound(choices.get(choice));
        if (upperBound < precision) {
          continue;
        }
        actionUpperBounds[choice] = upperBound;
        if (upperBound > bestValue) {
          maximalBestActions = PrismUtils.doublesAreEqual(upperBound, bestValue) ?
              maximalBestActions + 1 : 1;
          bestValue = upperBound;
        } else if (PrismUtils.doublesAreEqual(upperBound, bestValue)) {
          maximalBestActions += 1;
        }
      }

      if (bestValue == 0.0d) {
        return -1;
      }
      assert bestValue >= precision;

      int bestActionCount = 0;
      int[] bestActions = new int[maximalBestActions];
      for (int choice = 0; choice < choiceCount; choice++) {
        if (PrismUtils.doublesAreEqual(bestValue, actionUpperBounds[choice])) {
          bestActions[bestActionCount] = choice;
          bestActionCount += 1;
        }
      }

      // There has to be a witness for the bestValue
      assert bestActionCount > 0;
      distribution = choices.get(Util.sampleUniform(bestActions, bestActionCount));
      assert PrismUtils.doublesAreEqual(bestValue, stateUpdate.getExpectedDifference(distribution));
    }
    assert stateUpdate.getExpectedDifference(distribution) >= precision;

    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      int successor = distribution.getSupport().iterator().next();
      return stateUpdate.isSolved(successor) ? -1 : successor;
    }

    // Sample according to max reachability upper bound
    // int[] successors = distribution.getSupportArray();
    List<Integer> support = new ArrayList<>(distribution.getSupport());
    assert !support.isEmpty();
    double[] successorValues = new double[support.size()];

    int index = -1;
    for (int successor : support) {
      index++;
      if (stateUpdate.isSolved(successor)) {
        continue;
      }

      successorValues[index] = heuristic == SuccessorHeuristic.PROB
          ? distribution.get(successor)
          : stateUpdate.getDifference(successor);
    }
    int sample = Util.sample(successorValues);
    if (sample == -1) {
      return -1;
    }
    assert !stateUpdate.isSolved(support.get(sample));
    return support.get(sample);
  }

  private void explore(int state) throws PrismException {
    assert !explorer.isStateExplored(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);
  }

  private boolean collapse() throws PrismException {
    if (!newStatesSinceCollapse) {
      return false;
    }
    newStatesSinceCollapse = false;
    Util.mdpWithBoundsToDotFile("err.dot", new AnnotatedModel<>((MDP) explorer.getModel(),
        explorer::getState, explorer.getExploredStates()), stateUpdate, s -> false);
    List<NatBitSet> collapseStates = findCollapseStates();
    if (collapseStates.isEmpty()) {
      return false;
    }

    IntList representatives = explorer.collapse(collapseStates);
    Iterator<NatBitSet> collapseIterator = collapseStates.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while (collapseIterator.hasNext()) {
      NatBitSet states = collapseIterator.next();
      int representative = representativeIterator.nextInt();

      assert explorer.isStateExplored(representative);
      // Delete information of all collapsed states
      states.forEach((IntConsumer) stateUpdate::clear);
      // Update the representative
      update(representative);
    }

    return true;
  }

  private double update(int state) {
    return stateUpdate.update(state, explorer.getChoices(state));
  }

  protected abstract List<NatBitSet> findCollapseStates() throws PrismException;

  private String getProgressString() {
    Int2DoubleMap initialStateValues = new Int2DoubleAVLTreeMap();
    explorer.getModel().getInitialStates().forEach(initialState -> {
      int collapsedRepresentative = explorer.getCollapsedRepresentative(initialState);
      initialStateValues
          .put(initialState.intValue(), stateUpdate.getDifference(collapsedRepresentative));
    });

    return String.format("  Trials: %d, steps: %d, avg len: %f, backtrace count: %d, steps: %d%n"
            + "  States: %d/%d in partial/collapsed model (%d/%d fringe)%n"
            + "  Bounds: %s",
        samples, sampleSteps, (double) sampleSteps / (double) samples, backtraceCount,
        backtraceSteps,
        explorer.getPartialExplorer().exploredStateCount(), explorer.exploredStateCount(),
        explorer.getPartialExplorer().fringeStateCount(), explorer.fringeStateCount(),
        initialStateValues);
  }

  public static class MDPBuilder extends UnboundedSamplingBuilder<MDP> {
    private final NatBitSet statesInMec = NatBitSets.set();

    public MDPBuilder(PrismComponent prism, CollapsingExplorer<MDP> explorer,
        StateUpdate stateUpdate) {
      super(prism, explorer, stateUpdate);
    }

    @Override
    protected List<NatBitSet> findCollapseStates() throws PrismException {
      logger.log(Level.FINE, "\nStarting MECs collapse");

      CollapsingExplorer<MDP> explorer = getExplorer();
      NatBitSet collapsedModelStates = explorer.getExploredStates();

      ECComputerFast ecComputer = new ECComputerFast(prism);
      List<MEC> mecs = ecComputer.computeMECs(explorer.getModel(), collapsedModelStates::contains);

      if (mecs.isEmpty()) {
        logger.log(Level.FINE, "Found no MECs");
        return Collections.emptyList();
      }

      int statesInMecCount = statesInMec.size();
      List<NatBitSet> newMecStates = new ArrayList<>();
      for (MEC mec : mecs) {
        NatBitSet states = mec.states;
        assert !states.isEmpty();

        // Check if any of the states in this MEC have not been touched yet - it is a new MEC then.
        statesInMec.or(states);
        int newSize = statesInMec.size();
        if (newSize > statesInMecCount) {
          statesInMecCount = newSize;
          newMecStates.add(mec.states);
        }
      }
      assert !newMecStates.isEmpty();

      if (logger.isLoggable(Level.FINE)) {
        int mecStateCount = newMecStates.stream().mapToInt(NatBitSet::size).sum();
        logger.log(Level.FINE, "Found {0} new MECs with {0} states",
            new Object[] {newMecStates.size(), mecStateCount});
      }

      return newMecStates;
    }
  }

  public static class DTMCBuilder extends UnboundedSamplingBuilder<DTMC> {
    private final NatBitSet statesInBscc = NatBitSets.set();

    public DTMCBuilder(PrismComponent prism, CollapsingExplorer<DTMC> explorer,
        StateUpdate stateUpdate) {
      super(prism, explorer, stateUpdate);
    }

    @Override
    protected List<NatBitSet> findCollapseStates() throws PrismException {
      logger.log(Level.FINE, "\nStarting BSCC collapse");

      CollapsingExplorer<DTMC> explorer = getExplorer();
      NatBitSet collapsedModelStates = explorer.getExploredStates();

      SCCConsumerStore consumer = new SCCConsumerStore();
      SCCComputer sccComputer =
          SCCComputer.createSCCComputer(prism, explorer.getModel(), consumer);
      sccComputer.computeSCCs(false, collapsedModelStates::contains);
      List<BitSet> bsccs = consumer.getBSCCs();

      if (bsccs.isEmpty()) {
        logger.log(Level.FINE, "Found no BSCCs");
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
      assert !newBsccStates.isEmpty();

      if (logger.isLoggable(Level.FINE)) {
        int mecStateCount = newBsccStates.stream().mapToInt(NatBitSet::size).sum();
        logger.log(Level.FINE, "Found {0} new BSCCs with {1} states",
            new Object[] {newBsccStates.size(), mecStateCount});
      }

      return newBsccStates;
    }
  }
}
