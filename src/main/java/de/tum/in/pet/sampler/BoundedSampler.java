package de.tum.in.pet.sampler;

import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.implementation.core.ApproximationMarker;
import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.model.Model;
import de.tum.in.pet.util.Util;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.ValueVerdict;
import de.tum.in.pet.values.bounded.StateUpdateBounded;
import de.tum.in.pet.values.bounded.StateValuesBounded;
import de.tum.in.pet.values.bounded.StateValuesBoundedFunction;
import de.tum.in.pet.values.unbounded.StateValueFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import prism.PrismComponent;
import prism.PrismException;

public class BoundedSampler<S, M extends Model> implements Sampler<S, M> {
  private static final Logger logger = Logger.getLogger(BoundedSampler.class.getName());

  private static final long REPORT_PROGRESS_EVERY_STEPS = 10000000;

  protected final PrismComponent prism;
  protected final Explorer<S, M> explorer;
  private final StateUpdateBounded stateUpdate;
  private final ValueVerdict verdict;

  private final int stepBound;
  private final SuccessorHeuristic heuristic;
  private final StateValuesBounded stateValues;

  private long sampleSteps = 0;
  private long samples = 0;

  public BoundedSampler(PrismComponent prism, Explorer<S, M> explorer, int stepBound,
      StateUpdateBounded stateUpdate, ValueVerdict verdict, SuccessorHeuristic heuristic,
      StateValuesBounded stateValues) {
    this.prism = prism;
    this.explorer = explorer;
    this.stepBound = stepBound;
    this.stateUpdate = stateUpdate;
    this.verdict = verdict;
    this.heuristic = heuristic;
    this.stateValues = stateValues;
  }

  @Override
  public Explorer<S, M> explorer() {
    return explorer;
  }

  @Override
  public AnnotatedModel<M> model() {
    return new AnnotatedModel<>(explorer.model(),
        explorer::getState, explorer.exploredStates().clone());
  }

  @Override
  public Bounds bounds(int state) {
    return stateValues.bounds(state, stepBound);
  }

  @Override
  public void build() throws PrismException {
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
      while (!isSolved(initialState, stepBound)) {
        iterations += 1;
        iterationsSinceCheck += 1;

        long sampleTime = System.nanoTime();
        sample(initialState);
        timeInSampling += (System.nanoTime() - sampleTime);

        int currentStateCount = explorer.exploredStateCount();
        int newStatesSinceFullCheck = currentStateCount - statesInLastFullCheck;

        if (stateValues instanceof ApproximationMarker) {
          boolean check = false;
          if (newStatesSinceFullCheck >= checkNewStateDelay && iterationsSinceCheck > 500) {
            // TODO Depend on initial state fringe reachability or sum of new states?
            checkNewStateDelay = explorer().exploredStateCount() / 10;
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
            if (isConverged) { // NOPMD
              break;
            }
          }
        }
      }
    }

    long elapsedTime = System.nanoTime() - timer;

    if (logger.isLoggable(Level.INFO)) {
      double secondsToNanoseconds = (double) TimeUnit.SECONDS.toNanos(1L);

      Int2ObjectMap<Bounds> initialStateValues = new Int2ObjectAVLTreeMap<>();
      explorer.initialStates().forEach((int initialState) ->
          initialStateValues.put(initialState, stateValues.bounds(initialState, stepBound)));

      String progressString =
          String.format("%n== Finished %d-bounded sampling ==%n"
                  + "  Total samples: %d, steps: %d, avg len: %f%n"
                  + "  Total exact queries: %d%n"
                  + "  States: %d in partial model%n"
                  + "  Initial state bounds: %s%n"
                  + "  Time: %f sec (%f in sampling, %f in computation)%n",
              stepBound, samples, sampleSteps, (double) sampleSteps / (double) samples,
              exactComputations, explorer.exploredStateCount(), initialStateValues,
              elapsedTime / secondsToNanoseconds, timeInSampling / secondsToNanoseconds,
              timeInExact / secondsToNanoseconds);
      logger.log(Level.INFO, progressString);
    }
  }

  private boolean isSolved(int state, int remainingSteps) {
    return verdict.isSolved(stateValues.bounds(state, remainingSteps));
  }


  private int sample(int initialState) throws PrismException {
    assert !isSolved(initialState, stepBound);

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;

    int currentState = initialState;
    int remainingSteps = stepBound + 1;
    int exploreCount = 0;

    samples += 1;

    // Sample a path
    while (remainingSteps > 0) {
      assert remainingSteps == stepBound - visitedStates.size() + 1;

      sampleSteps++;
      if (sampleSteps % REPORT_PROGRESS_EVERY_STEPS == 0) {
        reportProgress();
      }

      visitedStates.add(currentState);
      remainingSteps -= 1;
      int nextState = sampleNextState(currentState, remainingSteps);
      if (nextState == -1) {
        // No successor available
        break;
      }

      if (!explorer.isExploredState(nextState)) {
        if (exploreCount == 5) {
          break;
        }
        exploreCount += 1;
        explore(nextState);
      }

      currentState = nextState;
    }

    while (!visitStack.isEmpty()) {
      currentState = visitStack.popInt();
      assert explorer.isExploredState(currentState);
      assert remainingSteps == stepBound - visitedStates.size();

      if (remainingSteps > 0) {
        update(currentState, remainingSteps);
      }

      remainingSteps += 1;
    }

    return exploreCount;
  }

  private Bounds update(int state, int remainingSteps) {
    return update(state, remainingSteps, explorer.getChoices(state));
  }

  private Bounds update(int state, int remainingSteps, List<Distribution> choices) {
    assert explorer.isExploredState(state);

    Bounds newValues = stateUpdate.update(state, remainingSteps, choices, stateValues);
    stateValues.setBounds(state, remainingSteps, newValues);

    // Consistency of state values
    assert (stateValues instanceof ApproximationMarker
        && stateValues.bounds(state, remainingSteps).contains(newValues))
        || stateValues.bounds(state, remainingSteps).equalsUpTo(newValues);
    return newValues;
  }

  private int sampleNextState(int state, int remainingSteps) {
    assert explorer.isExploredState(state);

    List<Distribution> choices = explorer.getChoices(state);

    // Deadlock state
    if (choices.isEmpty()) {
      return -1;
    }

    if (remainingSteps == 0) {
      // TODO Weird? Figure out when this is happening
      // CSOFF: Indentation
      return Util.sampleNextState(choices, heuristic,
          d -> stateValues.upperBound(state, remainingSteps, d),
          s -> stateValues.difference(s, remainingSteps),
          s -> false);
      // CSON: Indentation
    }

    // CSOFF: Indentation
    return Util.sampleNextState(choices, heuristic,
        d -> stateValues.upperBound(state, remainingSteps, d),
        s -> stateValues.difference(s, remainingSteps - 1),
        s -> false);
    // TODO Using below as ignore successors slows down convergence drastically on CYCLIN
    // s -> verdict.isSolved(stateValues.bounds(s, remainingSteps - 1))
    // CSON: Indentation
  }

  private void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    explorer.exploreState(state);
  }

  private boolean computeExactBounds(int state, int stepBound) {
    if (stepBound == 0) {
      return explorer.isExploredState(state);
    }

    M model = explorer.model();
    int numStates = model.getNumStates();

    Bounds[] init = new Bounds[numStates];
    for (int s = 0; s < numStates; s++) {
      init[s] = stateValues.bounds(s, 0);
    }

    Bounds[] vect = init;
    Bounds[] result = vect.clone();

    for (int steps = 1; steps <= stepBound; steps++) {
      Bounds[] swap = vect;
      vect = result;
      result = swap;

      IntIterator iterator = explorer.exploredStates().iterator();
      while (iterator.hasNext()) {
        int iterationState = iterator.nextInt();
        List<Distribution> choices = explorer.getChoices(iterationState);

        Bounds[] currentValues = vect;
        StateValuesBoundedFunction values = (i, r) -> currentValues[i];
        Bounds bounds = stateUpdate.update(iterationState, steps, choices, values);

        // Faithful approximation
        assert stateValues.bounds(iterationState, steps).contains(bounds);

        result[iterationState] = bounds;
      }

      int currentSteps = steps;
      Bounds[] currentResult = result;
      explorer.exploredStates()
          .forEach((int s) -> stateValues.setBounds(s, currentSteps, currentResult[s]));
    }

    return verdict.isSolved(result[state]);
  }

  public void reportProgress() {
    if (!logger.isLoggable(Level.FINE)) {
      return;
    }

    // writeDotModel("test.dot", null);

    Int2ObjectMap<Bounds> initialStateValues = new Int2ObjectAVLTreeMap<>();
    for (int initialState : explorer().initialStates()) {
      initialStateValues.put(initialState,
          stateValues.bounds(initialState, stepBound));
    }

    String progressString = String.format("%n== Progress Report ==%n"
            + "  Trials: %d, steps: %d, avg len: %f%n"
            + "  States: %d in partial model%n"
            + "  Initial state bounds: %s", samples, sampleSteps,
        (double) sampleSteps / (double) samples,
        explorer.exploredStateCount(), initialStateValues);
    logger.log(Level.FINE, progressString);
  }


  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void writeDotModel(String filename, @Nullable IntPredicate highlight) {
    logger.log(Level.WARNING, "Writing model as dot file!");
    StateValueFunction stateValueFunction = s -> stateValues.bounds(s, stepBound);
    Util.modelWithBoundsToDotFile(filename, explorer.model(), explorer,
        stateValueFunction, s -> true, highlight);
    int states = explorer.model().getNumStates();
    StringBuilder builder = new StringBuilder("\n");
    for (int state = 0; state < states; state++) {
      if (!explorer.isExploredState(state)) {
        continue;
      }
      builder.append(String.format("%10d:", state));
      for (int remainingSteps = stepBound; remainingSteps >= 0; remainingSteps--) {
        String bounds = String.format("%20s", stateValues.bounds(state, remainingSteps));
        builder.append(' ').append(bounds);
      }
      builder.append('\n');
    }
    logger.log(Level.FINE, builder.toString());
  }
}
