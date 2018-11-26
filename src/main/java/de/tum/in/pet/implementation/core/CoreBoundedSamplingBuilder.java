package de.tum.in.pet.implementation.core;

import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.model.Model;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.util.Sample;
import de.tum.in.pet.util.Util;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.StateValues;
import de.tum.in.pet.values.StateValuesBounded;
import explicit.PredecessorRelation;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
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
import prism.PrismComponent;
import prism.PrismException;

public class CoreBoundedSamplingBuilder<M extends Model> {
  private static final Logger logger = Logger.getLogger(CoreBoundedSamplingBuilder.class.getName());

  private static final long REPORT_PROGRESS_EVERY_STEPS = 10000000;

  protected final PrismComponent prism;
  protected final Explorer<M> explorer;

  private final SuccessorHeuristic heuristic;
  private final int stepBound;
  private final double precision;
  private final StateValuesBounded stateValuesBounded;

  private long sampleSteps = 0;
  private long samples = 0;
  private long longestPath = 0;

  public CoreBoundedSamplingBuilder(PrismComponent prism, Explorer<M> explorer, int stepBound,
      double precision, SuccessorHeuristic heuristic, StateValuesBounded stateValuesBounded) {
    this.prism = prism;
    this.explorer = explorer;
    this.stepBound = stepBound;
    this.precision = precision;
    this.heuristic = heuristic;
    this.stateValuesBounded = stateValuesBounded;
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

    StateValues initialValues = stateValuesBounded.stepValues(stepBound);
    for (int initialState : explorer.initialStates()) {
      while (initialValues.difference(initialState) > precision) {
        iterations += 1;
        iterationsSinceCheck += 1;

        long sampleTime = System.nanoTime();
        sampleFrom(initialState);
        timeInSampling += (System.nanoTime() - sampleTime);

        int currentStateCount = explorer.model().getNumStates();
        int newStatesSinceFullCheck = currentStateCount - statesInLastFullCheck;

        if (stateValuesBounded instanceof StateValuesBoundedCoreDense) {
          // Precise does not need re-computation
          continue;
        }
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

      Int2ObjectMap<Bounds> initialStateValues = new Int2ObjectAVLTreeMap<>();
      explorer.initialStates().forEach((int initialState) ->
          initialStateValues.put(initialState, initialValues.bounds(initialState)));

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
    for (Distribution distribution : explorer.getChoices(state)) {
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
      StateValues currentStepValues = stateValuesBounded.stepValues(steps);

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
            currentStepValues.upperBound(iterationState));

        result[iterationState] = reachability;

        predecessorRelation.getPredecessorsIterator(iterationState)
            .forEachRemaining(interestingStates::set);
      }

      double[] currentResult = result;
      explorer.exploredStates().forEach((int s) ->
          currentStepValues.setUpperBound(s, currentResult[s]));

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
          .filter(currentStepValues::isZeroUpperBound)
          .allMatch(s -> currentResult[s] == 0.0d);

      interestingStates.andNot(exploredOneStates);
      if (interestingStates.isEmpty()) {
        return currentResult[state] <= precision;
      }
    }

    return result[state] <= precision;
  }

  private int sampleNextState(int state, int successorRemainingSteps) {
    assert explorer.isExploredState(state);

    List<Distribution> choices = explorer.getChoices(state);

    // Deadlock state
    if (choices.isEmpty()) {
      stateValuesBounded.setZero(state);
      return -1;
    }

    if (successorRemainingSteps == 0) {
      // Special case: Need to pick an arbitrary successor randomly, since the upper bounds are
      // always zero
      Distribution distribution = Sample.sampleUniform(choices);
      if (distribution.isEmpty()) {
        return -1;
      }
      return Sample.sample(distribution);
    }

    StateValues stepValues = stateValuesBounded.stepValues(successorRemainingSteps);
    // CSOFF: Indentation
    return Util.sampleNextState(choices, heuristic,
        d -> stepValues.upperBound(state, d),
        stepValues::difference,
        stepValues::isZeroUpperBound);
    // CSON: Indentation
  }

  private int sampleFrom(int initialState) throws PrismException {
    samples++;

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;
    visitedStates.add(initialState);

    int currentState = initialState;
    int remainingSteps = stepBound;
    int exploreCount = 0;

    // Sample a path
    while (exploreCount < 5 && remainingSteps >= 0) {
      assert remainingSteps == stepBound - visitedStates.size() + 1;
      sampleSteps++;

      if (sampleSteps % REPORT_PROGRESS_EVERY_STEPS == 0) {
        reportProgress();
      }

      int nextState = sampleNextState(currentState, remainingSteps);
      if (nextState == -1) {
        // No successor available
        break;
      }

      visitedStates.add(nextState);
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
      assert remainingSteps == stepBound - visitedStates.size();
      remainingSteps += 1;

      if (remainingSteps > 0) {
        StateValues stepValues = stateValuesBounded.stepValues(remainingSteps);
        StateValues successorStepValues = stateValuesBounded.stepValues(remainingSteps - 1);
        IntToDoubleFunction successorValues = successorStepValues::upperBound;

        double upperBound = stepValues.upperBound(currentState);
        if (upperBound == 0.0d) {
          assert step(currentState, successorValues) == 0.0d;
        } else {
          double maxSuccessorBounds = step(currentState, successorValues);
          /*
          if (upperBound <= maxSuccessorBounds) {
            System.out.println(String.format("%3d %4d BREAK", remainingSteps, currentState));
            break;
          } */
          if (upperBound > maxSuccessorBounds) {
            stepValues.setUpperBound(currentState, maxSuccessorBounds);
          }
        }
      }
    }

    return exploreCount;
  }

  public void reportProgress() {
    if (!logger.isLoggable(Level.FINE)) {
      return;
    }

    Int2ObjectMap<Bounds> initialStateValues = new Int2ObjectAVLTreeMap<>();
    for (int initialState : getExplorer().initialStates()) {
      initialStateValues.put(initialState,
          stateValuesBounded.stepValues(stepBound).bounds(initialState));
    }

    String progressString = String.format("%n== Progress Report ==%n"
            + "  Trials: %d, steps: %d, avg len: %f, longest %d%n"
            + "  States: %d in partial model%n"
            + "  Initial state bounds: %s", samples, sampleSteps,
        (double) sampleSteps / (double) samples, longestPath,
        explorer.exploredStateCount(), initialStateValues);
    logger.log(Level.FINE, progressString);
  }
}
