package de.tum.in.prism.core.builder;

import com.google.common.collect.ImmutableList;
import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.prism.core.bounds.StepBoundApproximation;
import de.tum.in.prism.core.bounds.StepBoundApproximationSimple;
import de.tum.in.prism.core.explorer.DefaultDTMCExplorer;
import de.tum.in.prism.core.explorer.DefaultMDPExplorer;
import de.tum.in.prism.core.explorer.Explorer;
import de.tum.in.prism.core.util.SuccessorHeuristic;
import de.tum.in.prism.core.util.Util;
import explicit.Distribution;
import explicit.Model;
import explicit.PredecessorRelation;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import prism.ModelGenerator;
import prism.PrismComponent;
import prism.PrismException;

public abstract class BoundedSamplingBuilder<M extends Model> {
  private static final Logger logger = Logger.getLogger(BoundedSamplingBuilder.class.getName());

  protected final PrismComponent prism;

  private final Explorer<M> explorer;
  private final SuccessorHeuristic heuristic;
  private final int stepBound;
  private final double precision;

  private final StepBoundApproximation stepBoundApproximation;

  private int sampleSteps = 0;
  private int samples = 0;
  private int longestPath = 0;

  protected BoundedSamplingBuilder(PrismComponent prism, Explorer<M> explorer, int stepBound,
      double precision) {
    this.prism = prism;
    this.explorer = explorer;
    this.stepBound = stepBound;
    this.precision = precision;
    stepBoundApproximation = new StepBoundApproximationSimple(10);
    heuristic = SuccessorHeuristic.GUIDED;
  }

  public Explorer<M> getExplorer() {
    return explorer;
  }

  public AnnotatedModel<M> build() throws PrismException {
    logger.log(Level.INFO, "Learning core for step bound {0} and precision {1} {2}",
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

    for (int initialState : explorer.getInitialStates()) {
      while (stepBoundApproximation.getUpperBound(initialState, stepBound) > precision) {
        iterations += 1;
        iterationsSinceCheck += 1;

        long sampleTime = System.nanoTime();
        sampleFrom(initialState);
        timeInSampling += (System.nanoTime() - sampleTime);

        int currentStateCount = explorer.getModel().getNumStates();
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
      explorer.getInitialStates().forEach((int initialState) ->
          initialStateValues
              .put(initialState, stepBoundApproximation.getUpperBound(initialState, stepBound)));

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

    return new AnnotatedModel<M>(explorer.getModel(), explorer::getState, states);
  }

  private NatBitSet getActualReachableStates() {
    NatBitSet reachableStates = NatBitSets.set();
    explorer.getInitialStates().forEach((IntConsumer) reachableStates::set);
    NatBitSet newStates = NatBitSets.copyOf(reachableStates);

    int steps = 0;
    while (!newStates.isEmpty() && steps <= stepBound) {
      steps += 1;
      NatBitSet currentStates = newStates;
      NatBitSet nextStepStates = NatBitSets.set();

      currentStates.forEach((int state) -> getExplorer().getModel().getSuccessors(state)
          .forEachRemaining((int successor) -> {
            if (reachableStates.add(successor)) {
              nextStepStates.set(successor);
            }
          }));
      newStates = nextStepStates;
    }
    reachableStates.and(explorer.getExploredStates());
    return reachableStates;
  }

  protected double step(int state, IntToDoubleFunction values) {
    double maxSuccessorBounds = 0.0d;
    for (Distribution distribution : getDistributions(state)) {
      double distributionBounds = Util.sumWeighted(distribution, values);
      if (distributionBounds > maxSuccessorBounds) {
        maxSuccessorBounds = distributionBounds;
      }
    }
    return maxSuccessorBounds;
  }

  private boolean computeExactBounds(int s, int stepBound) {
    if (stepBound == 0) {
      return explorer.isStateExplored(s);
    }

    M model = explorer.getModel();
    int numStates = model.getNumStates();

    PredecessorRelation predecessorRelation = model.getPredecessorRelation(prism, false);

    // Initialize
    BoundedNatBitSet interestingStates = NatBitSets.boundedSet(numStates, NatBitSets.UNKNOWN_SIZE);
    BoundedNatBitSet exploredOneStates = NatBitSets.boundedSet(numStates, NatBitSets.UNKNOWN_SIZE);

    double[] init = new double[numStates];
    NatBitSets.complementIterator(explorer.getExploredStates(), numStates)
        .forEachRemaining((int state) -> {
          init[state] = 1.0d;
          predecessorRelation.getPredecessorsIterator(state)
              .forEachRemaining(interestingStates::set);
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
        int state = iterator.nextInt();
        assert explorer.isStateExplored(state);

        double[] currentValues = vect;
        if (vect[state] == 1.0d) {
          assert step(state, i -> currentValues[i]) == 1.0d;
          result[state] = 1.0d;
          exploredOneStates.set(state);
          continue;
        }

        double reachability = step(state, i -> currentValues[i]);

        // State was rightfully marked as interesting
        assert 0 < reachability;
        // Reachability is monotone for more steps
        assert Util.doublesAreLessOrEqual(result[state], reachability);
        // The upper bound approximation is indeed an upper bound
        assert Util.doublesAreLessOrEqual(reachability,
            stepBoundApproximation.getUpperBound(state, steps));

        result[state] = reachability;

        predecessorRelation.getPredecessorsIterator(state).forEachRemaining(interestingStates::set);
      }

      double[] currentResult = result;
      int currentSteps = steps;

      stepBoundApproximation
          .setUpperBounds(explorer.getExploredStates(), state -> currentResult[state],
              currentSteps);

      // Only explored states are interesting
      assert explorer.getExploredStates().containsAll(interestingStates);
      // Consistency of one-states
      assert exploredOneStates.intStream().allMatch((int state) -> currentResult[state] == 1.0d);
      // All non-explored states have value 1.0
      assert IntStream.range(0, numStates).filter(state -> !explorer.isStateExplored(state))
          .allMatch(state -> currentResult[state] == 1.0d);
      // All non-trivial states are interesting
      assert IntStream.range(0, numStates)
          .filter(state -> 0.0d < currentResult[state] && currentResult[state] < 1.0d)
          .allMatch(interestingStates::contains);
      // Previous zero states remain zero states
      assert IntStream.range(0, numStates)
          .filter(state -> stepBoundApproximation.isZero(state, currentSteps))
          .allMatch(state -> currentResult[state] == 0.0d);

      interestingStates.andNot(exploredOneStates);
      if (interestingStates.isEmpty()) {
        break;
      }

      /* if (currentResult[s] > precision) {
      return false;
      } */
    }

    /*
    setLog(new PrismDevNullLog());
    try {
    MDPModelChecker mc = new MDPModelChecker(this);
    BoundedNatBitSet fringeStates = NatBitSets.boundedFilledSet(model.getNumStates());
    fringeStates.andNot(explorer.getExploredStates());
    BitSet bitSet = NatBitSets.toBitSet(fringeStates);
    ModelCheckerResult mcResult = mc.computeBoundedReachProbs((explicit.MDP) model, bitSet,
    stepBound, false);
    double[] finalResult = result;
    for (int state : explorer.getExploredStates()) {
    assert mcResult.soln[state] == result[state] :
    stepBound + " " + result[state] + " " + step(state, i -> finalResult[i]) + " " +
    mcResult.soln[state];
    }
    } catch (PrismException e) {
    }
    setLog(log);
    */

    return result[s] <= precision;
  }

  private int sampleNextState(int state, int successorRemainingSteps,
      IntPredicate prohibitedSuccessors) {
    assert explorer.isStateExplored(state);

    List<Distribution> choices = getDistributions(state);

    // Deadlock state
    if (choices.isEmpty()) {
      stepBoundApproximation.setZero(state);
      return -1;
    }

    Int2DoubleMap successorWeights = new Int2DoubleOpenHashMap();

    choices.forEach(choice -> choice.forEach(entry -> {
      int successor = entry.getKey();
      double transitionProbability = entry.getValue();
      if (stepBoundApproximation.isZero(successor, successorRemainingSteps) || prohibitedSuccessors
          .test(successor)) {
        return;
      }
      double sampleProbability = transitionProbability;
      if (heuristic == SuccessorHeuristic.GUIDED) {
        sampleProbability *= stepBoundApproximation
            .getUpperBound(successor, successorRemainingSteps);
      }
      successorWeights.merge(successor, sampleProbability, Double::max);
    }));
    if (successorWeights.isEmpty()) {
      return -1;
    }

    return Util.sample(successorWeights);
  }

  private int sampleFrom(int initialState) throws PrismException {
    samples++;

    int currentState = initialState;
    int remainingSteps = stepBound;
    int exploreCount = 0;

    NatBitSet visitedStateSet = NatBitSets.set();
    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;

    // Sample a path
    while (exploreCount < 5 && remainingSteps > -1) {
      sampleSteps++;

      visitedStates.add(currentState);
      visitedStateSet.set(currentState);
      int nextState = sampleNextState(currentState, Math.max(remainingSteps - 1, 1),
          visitedStateSet::contains);
      if (nextState == -1) {
        // No successor available
        break;
      }
      remainingSteps -= 1;
      currentState = nextState;

      if (!explorer.isStateExplored(currentState)) {
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
      assert explorer.isStateExplored(currentState);

      if (remainingSteps > 1) {
        int currentRemaining = remainingSteps;
        IntToDoubleFunction
            successorValues = successor -> stepBoundApproximation
            .getUpperBound(successor, currentRemaining - 1);

        double upperBound = stepBoundApproximation.getUpperBound(currentState, currentRemaining);
        if (upperBound == 0.0d) {
          assert step(currentState, successorValues) == 0.0d;
          continue;
        }

        double maxSuccessorBounds = step(currentState, successorValues);
        if (upperBound <= maxSuccessorBounds) {
          break;
        }
        stepBoundApproximation.setUpperBound(currentState, currentRemaining, maxSuccessorBounds);
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
    for (int initialState : getExplorer().getInitialStates()) {
      initialStateValues
          .put(initialState, stepBoundApproximation.getUpperBound(initialState, stepBound));
    }

    String progressString = String.format("%n== Progress Report ==%n"
            + "  Trials: %d, steps: %d, avg len: %f, longest %d%n"
            + "  States: %d in partial model%n"
            + "  Initial state bounds: %s", samples, sampleSteps,
        (double) sampleSteps / (double) samples, longestPath,
        explorer.exploredStateCount(),
        initialStateValues);
    logger.log(Level.FINE, progressString);
  }

  protected abstract List<Distribution> getDistributions(int state);

  public static class DTMC extends BoundedSamplingBuilder<explicit.DTMC> {
    public DTMC(PrismComponent prism, ModelGenerator generator, int stepBound, double precision)
        throws PrismException {
      super(prism, new DefaultDTMCExplorer(generator), stepBound, precision);
    }

    @Override
    protected List<Distribution> getDistributions(int state) {
      Distribution distribution = getExplorer().getDistribution(state);
      return distribution == null ? ImmutableList.of() : ImmutableList.of(distribution);
    }

    @Override
    public Explorer.DTMCExplorer getExplorer() {
      return (Explorer.DTMCExplorer) super.getExplorer();
    }
  }

  public static class MDP extends BoundedSamplingBuilder<explicit.MDP> {
    public MDP(PrismComponent prism, ModelGenerator generator, int stepBound, double precision)
        throws PrismException {
      super(prism, new DefaultMDPExplorer(generator, s -> { }), stepBound, precision);
    }

    @Override
    protected List<Distribution> getDistributions(int state) {
      return getExplorer().getChoices(state);
    }

    @Override
    public Explorer.MDPExplorer getExplorer() {
      return (Explorer.MDPExplorer) super.getExplorer();
    }
  }
}
