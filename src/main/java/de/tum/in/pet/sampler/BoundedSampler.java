package de.tum.in.pet.sampler;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.Arrays;
import java.util.List;
import prism.PrismException;

public class BoundedSampler<S, M extends Model> implements Iterator<S, M> {
  private final Explorer<S, M> explorer;
  private final BoundedValues values;
  private final BoundedStepFunction function;

  private final int stepBound;

  public BoundedSampler(Explorer<S, M> explorer, int stepBound, BoundedValues values,
      BoundedStepFunction function) {
    this.values = values;
    this.explorer = explorer;
    this.stepBound = stepBound;
    this.function = function;
  }

  @Override
  public Explorer<S, M> explorer() {
    return explorer;
  }

  @Override
  public AnnotatedModel<M> model() {
    return new AnnotatedModel<>(explorer.model(), explorer::getState, explorer.exploredStates());
  }

  @Override
  public Bounds bounds(int state) {
    return values.bounds(state, stepBound);
  }

  @Override
  public void run() throws PrismException {
    int iterations = 0;
    int checkNewStateDelay = 50;
    int checkIterationDelay = 500;
    int iterationsSinceCheck = 0;
    int statesInLastFullCheck = 0;

    for (int initialState : explorer.initialStates()) {
      values.explored(initialState, stepBound);

      while (!values.isSolved(initialState, stepBound)) {
        iterations += 1;
        iterationsSinceCheck += 1;

        sample(initialState);

        if (values.storesExact()) {
          continue;
        }

        int currentStateCount = explorer.exploredStateCount();
        int newStatesSinceFullCheck = currentStateCount - statesInLastFullCheck;

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
          statesInLastFullCheck = currentStateCount;
          iterationsSinceCheck = 0;
          computeExactBounds();
        }
      }
    }
  }

  private void sample(int initialState) throws PrismException {
    assert !values.isSolved(initialState, stepBound);

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;

    int exploreCount = 0;
    int currentState = initialState;
    int remainingSteps = stepBound + 1;
    while (remainingSteps > 0) {
      assert explorer.isExploredState(currentState);
      assert remainingSteps == stepBound - visitedStates.size() + 1;

      visitStack.push(currentState);
      remainingSteps -= 1;

      if (remainingSteps == 0) {
        break;
      }
      List<Distribution> choices = explorer.getChoices(currentState);
      int nextState = values.sampleNextState(currentState, remainingSteps, choices);
      if (nextState == -1) {
        // No successor available
        break;
      }

      if (!explorer.isExploredState(nextState)) {
        if (exploreCount == 5) {
          break;
        }
        exploreCount += 1;
        values.explored(nextState, remainingSteps - 1);
        explorer.exploreState(nextState);
      }

      currentState = nextState;
    }

    while (!visitStack.isEmpty()) {
      int state = visitStack.popInt();
      assert explorer.isExploredState(state);
      assert remainingSteps == stepBound - visitedStates.size();

      if (remainingSteps > 0) {
        values.update(state, remainingSteps, explorer.getChoices(state));
      }

      remainingSteps += 1;
    }
  }

  private void computeExactBounds() {
    M model = explorer.model();
    int numStates = model.getNumStates();

    Bounds[] init = new Bounds[numStates];
    Arrays.setAll(init, s -> values.bounds(s, 0));

    Bounds[] vect = init;
    Bounds[] result = vect.clone();

    for (int remaining = 1; remaining <= stepBound; remaining++) {
      IntIterator iterator = explorer.exploredStates().iterator();
      while (iterator.hasNext()) {
        int state = iterator.nextInt();

        Bounds bounds = function.step(state, remaining, explorer.getChoices(state), vect);
        assert values.bounds(state, remaining).contains(bounds);
        result[state] = bounds;
      }
      if (values.stores(remaining)) {
        IntIterator resultIterator = explorer.exploredStates().iterator();
        while (resultIterator.hasNext()) {
          int state = resultIterator.nextInt();
          values.update(state, remaining, result[state]);
        }
      }

      Bounds[] swap = vect;
      vect = result;
      result = swap;
    }
  }
}
