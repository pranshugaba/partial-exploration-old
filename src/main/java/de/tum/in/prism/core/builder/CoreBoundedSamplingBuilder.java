package de.tum.in.prism.core.builder;

import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.bounds.StateUpdateBounded;
import de.tum.in.prism.core.bounds.StateUpdateBoundedCoreApproximationSimple;
import de.tum.in.prism.core.explorer.DefaultDTMCExplorer;
import de.tum.in.prism.core.explorer.DefaultMDPExplorer;
import de.tum.in.prism.core.explorer.EmbeddingCTMCExplorer;
import de.tum.in.prism.core.explorer.Explorer;
import de.tum.in.prism.core.explorer.UniformizingCTMCExplorer;
import de.tum.in.prism.core.util.SuccessorHeuristic;
import de.tum.in.prism.core.util.Util;
import de.tum.in.prism.util.DTMC;
import de.tum.in.prism.util.Distribution;
import de.tum.in.prism.util.MDP;
import explicit.Model;
import explicit.PredecessorRelation;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.IntToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import prism.ModelGenerator;
import prism.PrismComponent;
import prism.PrismException;

public abstract class CoreBoundedSamplingBuilder<M extends Model> {
  private static final Logger logger = Logger.getLogger(CoreBoundedSamplingBuilder.class.getName());

  protected final PrismComponent prism;
  protected final Explorer<M> explorer;

  private final SuccessorHeuristic heuristic;
  private final int stepBound;
  private final double precision;
  private final StateUpdateBounded stateUpdateBounded;

  private long sampleSteps = 0;
  private long samples = 0;
  private long longestPath = 0;

  protected CoreBoundedSamplingBuilder(PrismComponent prism, Explorer<M> explorer, int stepBound,
      double precision, SuccessorHeuristic heuristic) {
    this.prism = prism;
    this.explorer = explorer;
    this.stepBound = stepBound;
    this.precision = precision;
    this.heuristic = heuristic;
    stateUpdateBounded = new StateUpdateBoundedCoreApproximationSimple(5);
  }

  public Explorer<M> getExplorer() {
    return explorer;
  }

  public AnnotatedModel<M> build() throws PrismException {
    logger.log(Level.INFO, "Learning core for step bound {0} and precision {1}",
        new Object[] {stepBound, precision});

    long timer = System.nanoTime();

    long timeInSampling = 0L;
    long timeInExact = 0L;

    int exactComputations = 0;
    int iterations = 0;
    int checkNewStateDelay = 50;
    int checkIterationDelay = 500;
    int iterationsSinceCheck = 0;
    int statesInLastFullCheck = 0;

    for (int initialState : explorer.initialStates()) {
      while (stateUpdateBounded.getUpperBound(initialState, stepBound) > precision) {
        iterations += 1;
        iterationsSinceCheck += 1;

        long sampleTime = System.nanoTime();
        sampleFrom(initialState);
        timeInSampling += (System.nanoTime() - sampleTime);

        int currentStateCount = explorer.model().getNumStates();
        int newStatesSinceFullCheck = currentStateCount - statesInLastFullCheck;

        boolean check = false;

        if (newStatesSinceFullCheck >= checkNewStateDelay && iterationsSinceCheck > 500) {
          // TODO Depend on initial state fringe reachability or sum of new states?
          checkNewStateDelay = getExplorer().exploredStateCount() / 10;
          check = true;
        }
        if (iterations % checkIterationDelay == 0) {
          check = true;
          checkIterationDelay *= 2;
        }

        if (check) {
          long checkTime = System.nanoTime();
          boolean isConverged = computeExactBounds(initialState, stepBound);
          timeInExact += System.nanoTime() - checkTime;

          exactComputations += 1;
          statesInLastFullCheck = currentStateCount;
          iterationsSinceCheck = 0;
          reportProgress();
          if (isConverged) {
            break;
          }
        }
      }
    }

    NatBitSet states = getActualReachableStates();

    timer = System.nanoTime() - timer;

    if (logger.isLoggable(Level.INFO)) {
      double secondsToNanoseconds = (double) TimeUnit.SECONDS.toNanos(1L);

      Int2DoubleMap initialStateValues = new Int2DoubleAVLTreeMap();
      explorer.initialStates().forEach((int initialState) ->
          initialStateValues.put(initialState,
              stateUpdateBounded.getUpperBound(initialState, stepBound)));

      String progressString =
          String.format("%n== Finished finite core (precision %g, step bound %d) ==%n"
                  + "  Total samples: %d, steps: %d, avg len: %f%n"
                  + "  Total exact queries: %d%n"
                  + "  States: %d in partial model (%d reachable)%n"
                  + "  Initial state bounds: %s%n"
                  + "  Time: %f sec (%f in sampling, %f in computation)%n",
              precision, stepBound, samples, sampleSteps, (double) sampleSteps / (double) samples,
              exactComputations, explorer.exploredStateCount(),
              states.size(), initialStateValues, timer / secondsToNanoseconds,
              timeInSampling / secondsToNanoseconds,
              timeInExact / secondsToNanoseconds);
      logger.log(Level.INFO, progressString);
    }

    return new AnnotatedModel<>(explorer.model(), explorer::getState, states);
  }

  private NatBitSet getActualReachableStates() {
    NatBitSet reachableStates = NatBitSets.set();
    explorer.initialStates().forEach((IntConsumer) reachableStates::set);
    NatBitSet newStates = NatBitSets.copyOf(reachableStates);

    int steps = 0;
    while (!newStates.isEmpty() && steps <= stepBound) {
      steps += 1;
      NatBitSet currentStates = newStates;
      NatBitSet nextStepStates = NatBitSets.set();

      currentStates.forEach((int state) -> getExplorer().model().getSuccessors(state)
          .forEachRemaining((int successor) -> {
            if (reachableStates.add(successor)) {
              nextStepStates.set(successor);
            }
          }));
      newStates = nextStepStates;
    }
    reachableStates.and(explorer.exploredStates());
    return reachableStates;
  }

  protected double step(int state, IntToDoubleFunction values) {
    double maxSuccessorBounds = 0.0d;
    for (Distribution distribution : getDistributions(state)) {
      double distributionBounds = distribution.sumWeighted(values);
      if (distributionBounds > maxSuccessorBounds) {
        maxSuccessorBounds = distributionBounds;
      }
    }
    return maxSuccessorBounds;
  }

  private boolean computeExactBounds(int state, int stepBound) {
    if (stepBound == 0) {
      return explorer.isExploredState(state);
    }

    M model = explorer.model();
    int numStates = model.getNumStates();

    PredecessorRelation predecessorRelation = model.getPredecessorRelation(prism, false);

    // Initialize
    BoundedNatBitSet interestingStates = NatBitSets.boundedSet(numStates, NatBitSets.UNKNOWN_SIZE);
    BoundedNatBitSet exploredOneStates = NatBitSets.boundedSet(numStates, NatBitSets.UNKNOWN_SIZE);

    double[] init = new double[numStates];
    NatBitSets.complementIterator(explorer.exploredStates(), numStates)
        .forEachRemaining((int s) -> {
          init[s] = 1.0d;
          predecessorRelation.getPredecessorsIterator(s).forEachRemaining(interestingStates::set);
        });

    double[] vect = init;
    double[] result = vect.clone();

    // TODO Since this is BFS, we get a "distance to fringe", maybe use this as guidance, too
    // (prioritize "downward" flow)
    for (int steps = 1; steps <= stepBound; steps++) {
      double[] swap = vect;
      vect = result;
      result = swap;

      IntIterator iterator = NatBitSets.copyOf(interestingStates).iterator();
      while (iterator.hasNext()) {
        int iterationState = iterator.nextInt();
        assert explorer.isExploredState(iterationState);

        double[] currentValues = vect;
        if (vect[iterationState] == 1.0d) {
          assert step(iterationState, i -> currentValues[i]) == 1.0d;
          result[iterationState] = 1.0d;
          exploredOneStates.set(iterationState);
          continue;
        }

        double reachability = step(iterationState, i -> currentValues[i]);

        // State was rightfully marked as interesting
        assert 0 < reachability;
        // Reachability is monotone for more steps
        assert Util.doublesAreLessOrEqual(result[iterationState], reachability);
        // The upper bound approximation is indeed an upper bound
        assert Util.doublesAreLessOrEqual(reachability,
            stateUpdateBounded.getUpperBound(iterationState, steps));

        result[iterationState] = reachability;

        predecessorRelation.getPredecessorsIterator(iterationState)
            .forEachRemaining(interestingStates::set);
      }

      double[] currentResult = result;
      int currentSteps = steps;

      explorer.exploredStates().forEach((int s) ->
          stateUpdateBounded.setUpperBound(s, currentSteps, currentResult[s]));

      // Only explored states are interesting
      assert explorer.exploredStates().containsAll(interestingStates);
      // Consistency of one-states
      assert exploredOneStates.intStream().allMatch((int s) -> currentResult[s] == 1.0d);
      // All non-explored states have value 1.0
      assert IntStream.range(0, numStates).filter(s -> !explorer.isExploredState(s))
          .allMatch(s -> currentResult[s] == 1.0d);
      // All non-trivial states are interesting
      assert IntStream.range(0, numStates)
          .filter(s -> 0.0d < currentResult[s] && currentResult[s] < 1.0d)
          .allMatch(interestingStates::contains);
      // Previous zero states remain zero states
      assert IntStream.range(0, numStates)
          .filter(s -> stateUpdateBounded.isZeroUpperBound(s, currentSteps))
          .allMatch(s -> currentResult[s] == 0.0d);

      interestingStates.andNot(exploredOneStates);
      if (interestingStates.isEmpty()) {
        return false;
      }

      /* if (currentResult[s] > precision) {
      return false;
      } */
    }

    return result[state] <= precision;
  }

  private int sampleNextState(int state, int successorRemainingSteps) {
    assert explorer.isExploredState(state);

    List<Distribution> choices = getDistributions(state);

    // Deadlock state
    if (choices.isEmpty()) {
      stateUpdateBounded.setZero(state);
      return -1;
    }

    return Util.sampleNextState(choices, heuristic,
        s -> stateUpdateBounded.getUpperBound(s, successorRemainingSteps),
        d -> stateUpdateBounded.getUpperBound(state, successorRemainingSteps, d),
        s -> stateUpdateBounded.isZeroUpperBound(s, successorRemainingSteps));
  }

  private int sampleFrom(int initialState) throws PrismException {
    samples++;

    int currentState = initialState;
    int remainingSteps = stepBound;
    int exploreCount = 0;

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;

    // Sample a path
    while (exploreCount < 5 && remainingSteps > -1) {
      sampleSteps++;

      visitedStates.add(currentState);
      int nextState = sampleNextState(currentState, Math.max(remainingSteps - 1, 1));
      if (nextState == -1) {
        // No successor available
        break;
      }
      remainingSteps -= 1;
      currentState = nextState;

      if (!explorer.isExploredState(currentState)) {
        // Explore the state and continue sampling from here
        exploreCount++;
        explorer.exploreState(currentState);
      }
    }

    if (visitedStates.size() > longestPath) {
      longestPath = visitedStates.size();
    }

    while (!visitStack.isEmpty()) {
      currentState = visitStack.popInt();
      assert explorer.isExploredState(currentState);

      if (remainingSteps > 1) {
        int currentRemaining = remainingSteps;
        IntToDoubleFunction successorValues = successor ->
            stateUpdateBounded.getUpperBound(successor, currentRemaining - 1);

        double upperBound = stateUpdateBounded.getUpperBound(currentState, currentRemaining);
        if (upperBound == 0.0d) {
          assert step(currentState, successorValues) == 0.0d;
          continue;
        }

        double maxSuccessorBounds = step(currentState, successorValues);
        if (upperBound <= maxSuccessorBounds) {
          break;
        }
        stateUpdateBounded.setUpperBound(currentState, currentRemaining, maxSuccessorBounds);
      }
      remainingSteps += 1;
    }

    return exploreCount;
  }

  public void reportProgress() {
    if (!logger.isLoggable(Level.FINE)) {
      return;
    }

    Int2DoubleMap initialStateValues = new Int2DoubleAVLTreeMap();
    for (int initialState : getExplorer().initialStates()) {
      initialStateValues.put(initialState,
          stateUpdateBounded.getUpperBound(initialState, stepBound));
    }

    String progressString = String.format("%n== Progress Report ==%n"
            + "  Trials: %d, steps: %d, avg len: %f, longest %d%n"
            + "  States: %d in partial model%n"
            + "  Initial state bounds: %s", samples, sampleSteps,
        (double) sampleSteps / (double) samples, longestPath,
        explorer.exploredStateCount(), initialStateValues);
    logger.log(Level.FINE, progressString);
  }

  protected abstract List<Distribution> getDistributions(int state);

  public static class DTMCBuilder extends CoreBoundedSamplingBuilder<DTMC> {
    public DTMCBuilder(PrismComponent prism, ModelGenerator generator, int stepBound,
        double precision, SuccessorHeuristic heuristic) throws PrismException {
      super(prism, new DefaultDTMCExplorer(generator), stepBound, precision, heuristic);
    }

    @Override
    protected List<Distribution> getDistributions(int state) {
      List<Distribution> distributions = explorer.getChoices(state);
      assert distributions.size() <= 1;
      return distributions;
    }
  }

  public static class CTMCUnfiformizingBuilder extends CoreBoundedSamplingBuilder<DTMC> {
    public CTMCUnfiformizingBuilder(PrismComponent prism, ModelGenerator generator, int stepBound,
        double precision, double rate, SuccessorHeuristic heuristic) throws PrismException {
      super(prism, new UniformizingCTMCExplorer(generator, rate), stepBound, precision, heuristic);
    }

    @Override
    protected List<Distribution> getDistributions(int state) {
      List<Distribution> distributions = explorer.getChoices(state);
      assert distributions.size() <= 1;
      return distributions;
    }
  }

  public static class CTMCEmbeddingBuilder extends CoreBoundedSamplingBuilder<DTMC> {
    public CTMCEmbeddingBuilder(PrismComponent prism, ModelGenerator generator, int stepBound,
        double precision, SuccessorHeuristic heuristic) throws PrismException {
      super(prism, new EmbeddingCTMCExplorer(generator), stepBound, precision, heuristic);
    }

    @Override
    protected List<Distribution> getDistributions(int state) {
      List<Distribution> distributions = explorer.getChoices(state);
      assert distributions.size() <= 1;
      return distributions;
    }
  }

  public static class MDPBuilder extends CoreBoundedSamplingBuilder<MDP> {
    public MDPBuilder(PrismComponent prism, ModelGenerator generator, int stepBound,
        double precision, SuccessorHeuristic heuristic) throws PrismException {
      super(prism, new DefaultMDPExplorer(generator), stepBound, precision, heuristic);
    }

    @Override
    protected List<Distribution> getDistributions(int state) {
      return explorer.getChoices(state);
    }
  }
}
