package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;

import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;

public class BlackUnboundedReachValues extends UnboundedReachValues{

  // Returns the confidence width for a state x and it's corresponding action index y
  private Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction = x -> (y -> (0));

  private Int2ObjectMap<Bounds> oldBounds;

  public BlackUnboundedReachValues(ValueUpdate update, IntPredicate target, double precision, SuccessorHeuristic heuristic) {
    super(update, target, precision, heuristic);
  }

  /**
   * Setter for confidenceWidthFunction.
   */
  public void setConfidenceWidthFunction(Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction){
    this.confidenceWidthFunction = confidenceWidthFunction;
  }

  public void resetConfidenceWidthFunction(){
    this.confidenceWidthFunction = x -> (y -> (0));
  }

  /**
   * Creates a copy of the current bounds.
   */
  public void cacheCurrBounds(){
    oldBounds = new Int2ObjectOpenHashMap<>(this.bounds);
  }

  /**
   * Checks whether there have been any significant changes in values compared to the last time the cacheCurrBounds()
   * was called
   * @return true if there are changes, else false
   */
  public boolean checkProgress(){
    for(int state: bounds.keySet()){
      if(!oldBounds.containsKey(state)||!((Math.abs(bounds.get(state).upperBound()-oldBounds.get(state).upperBound())<1e-6)&&
              (Math.abs(bounds.get(state).lowerBound()-oldBounds.get(state).lowerBound())<1e-6))){
        return true;
      }
    }
    return false;
  }

  /**
   * Function to sample the index of the next action from choices from state.
   * @param state: Current state from which the action originates.
   * @param choices: Set of candidate choices.
   * @return Index of the sampled action corresponding to choices.
   */
  @Override
  public int sampleNextAction(int state, List<Distribution> choices){

    ToDoubleFunction<Integer> actionScore = i -> choices.get(i).isEmpty()
            ? 1 : isSmallestFixPoint()
                  ? 1.0d - choices.get(i).sumWeighted(this::lowerBound)
                  : successorBounds(state, choices.get(i), confidenceWidthFunction.apply(state).applyAsDouble(i)).upperBound();

    return SampleUtil.getOptimalChoice(choices, actionScore);
  }

  /**
   * Returns the bounds of an action according to the modified Bellman Equations equations in the CAV'19 paper. They
   * have been slightly modified according to Section 3.6.
   * @param state: originating state. If the distribution is void, the bounds of state are returned.
   * @param distribution: The probability distribution for sampling the next successor for an action.
   * @param confidenceWidth: The confidence width for a state-action pair.
   * @return Bounds of an action from a state with some confidence width.
   */
  private Bounds successorBounds(int state, Distribution distribution, double confidenceWidth) {
    if (distribution.support().size()==0){
      return Bounds.reachUnknown();
    }
    double lower = 0.0d;
    double upper = 0.0d;
    double sum = 0.0d;
    double minLower = 1;
    double maxUpper = 0;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      Bounds successorBounds = bounds(successor);
      double probability = Math.max(0, entry.getDoubleValue()-confidenceWidth);
      sum += probability;
      lower += successorBounds.lowerBound() * probability;
      upper += successorBounds.upperBound() * probability;
      minLower = Math.min(minLower, successorBounds.lowerBound());
      maxUpper = Math.max(maxUpper, successorBounds.upperBound());
    }
    if (sum == 0.0d) {
      bounds(state);
    }
    double remProb = 1-sum;
    minLower = 0;
    return Bounds.reach(lower+remProb*minLower, upper+remProb*maxUpper);
  }

  /**
   * The deflate algorithm as mentioned in Algorithm 6 of the CAV'19 paper. For a set states (probably in an MEC), this
   * sets the upper bound of all states to the upper bound of the best action leaving the set of states.
   * @param states: set of states, whose values are to be deflated.
   * @param choiceFunction: function that returns the choices for a given state.
   */
  public void deflate(IntSet states, Int2ObjectFunction<List<Distribution>> choiceFunction){
    double newUpperBound;

    if (update == ValueUpdate.MAX_VALUE) {
      newUpperBound = 0.0d;
      for (int state: states){
        List<Distribution> choices = choiceFunction.get(state);
        for(int i=0; i<choices.size(); i++){
          Distribution distribution = choices.get(i);
          // checks if the action's support is contained within the set of states. This means this action doesn't leave
          // the set of states.
          if(states.containsAll(distribution.support())){
            continue;
          }
          newUpperBound = Math.max(newUpperBound, successorBounds(state, distribution,
                  confidenceWidthFunction.get(state).get(i)).upperBound());
        }
      }
    } else {
      assert update == ValueUpdate.MIN_VALUE;

      newUpperBound = 1.0d;
      for (int state: states){
        List<Distribution> choices = choiceFunction.get(state);
        for(int i=0; i<choices.size(); i++){
          Distribution distribution = choices.get(i);
          if(distribution.support().containsAll(states)){
            continue;
          }
          newUpperBound = Math.min(newUpperBound, successorBounds(state, distribution,
                  confidenceWidthFunction.get(state).get(i)).upperBound());
        }
      }
    }

    for (int state: states){
      if (upperBound(state)>newUpperBound) {
        bounds.put(state, Bounds.of(lowerBound(state), newUpperBound));
      }
    }
  }

  // updates bound of a state according to the given list of distributions
  public void update(int state, List<Distribution> choices) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    Bounds stateBounds = bounds(state);
    if (isOne(stateBounds.lowerBound()) || isZero(stateBounds.upperBound())) {
      return;
    }
    assert !target.test(state);

    Bounds newBounds;
    // If there are no choices from the state, it must have a zero value (u=0, l=0)
    if (choices.isEmpty()) {
      newBounds = Bounds.reachZero();
      bounds.put(state, newBounds);
    }
    else if (choices.size() == 1) {
      newBounds = successorBounds(state, choices.get(0), confidenceWidthFunction.get(state).get(0));
      bounds.put(state, newBounds);
    }
    else {
      double newLowerBound;
      double newUpperBound;

      // finds bounds for each distribution using successorBounds. The new upper and lower bound are either the maximum or minimum of all computed respective bounds

      if (update == ValueUpdate.MAX_VALUE) {
        newLowerBound = 0.0d;
        newUpperBound = 0.0d;
        for (int distributionIndex=0; distributionIndex<choices.size(); distributionIndex++) {
          Bounds bounds = successorBounds(state, choices.get(distributionIndex),
                  confidenceWidthFunction.get(state).get(distributionIndex));
          double upperBound = bounds.upperBound();
          if (upperBound > newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound > newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      } else {
        assert update == ValueUpdate.MIN_VALUE;

        newUpperBound = 1.0d;
        newLowerBound = 1.0d;
        for (int distributionIndex=0; distributionIndex<choices.size(); distributionIndex++) {
          Bounds bounds = successorBounds(state, choices.get(distributionIndex),
                  confidenceWidthFunction.get(state).get(distributionIndex));
          double upperBound = bounds.upperBound();
          if (upperBound < newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound < newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      }

      assert newLowerBound <= newUpperBound;
      newBounds = Bounds.of(newLowerBound, newUpperBound);
      bounds.put(state, newBounds);
    }
  }

}
