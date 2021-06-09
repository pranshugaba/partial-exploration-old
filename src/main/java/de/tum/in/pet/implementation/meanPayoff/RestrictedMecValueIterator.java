package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.ints.*;
import parser.State;

import java.util.List;

public class RestrictedMecValueIterator<M extends Model> {

  public final M model; // Original Model
  public final Mec mec; // Mec with respect to original model
  public final double targetPrecision;
  public final Int2DoubleOpenHashMap values;  // map of states and total values; the average value can be obtained by dividing by iterCount
  public final RewardGenerator<State> rewardGenerator;
  private int iterCount;  // number of iterations in value iteration
  private final Int2ObjectFunction<State> stateIndexMap; // map from original model state number to corresponding state object

  private Bounds bounds;

  public RestrictedMecValueIterator(M model, Mec mec, double targetPrecision, RewardGenerator<State> rewardGenerator,
                                    Int2ObjectFunction<State> stateIndexMap){
    this.model = model;
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = new Int2DoubleOpenHashMap();
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
  }

  public RestrictedMecValueIterator(M model, Mec mec, double targetPrecision, RewardGenerator<State> rewardGenerator,
                                    Int2ObjectFunction<State> stateIndexMap, Int2DoubleOpenHashMap values){
    this.model = model;
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
  }
  //
  public void run(){
    int numStates = mec.size();
    NatBitSet states = mec.states;
    double[] diff =new double[numStates];  // This array stores the difference of values for each state between two successive iterations.

    if(values.size()==0) { // no pre-computed values sent
      // TODO loop over mec states
      IntIterator stateIterator = states.iterator();
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        values.put(state, 0.0);
      }
    }

    double max, min;
    do {
      Int2DoubleOpenHashMap oldValues = new Int2DoubleOpenHashMap(values);
      IntIterator stateIterator = states.iterator();
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        double maxActionValue = 0.0;
        IntSet allowedActions = mec.actions.get(state);  // TODO allowedActions numbered as in original model?
        assert allowedActions != null;
        List<Action> choices = model.getActions(state);  // get all actions (not distributions)
        // TODO Get Actions from original model, filter according to mec actions
        for (int action : allowedActions) {  // find the value of the state over all actions
          // Send action label instead of action object. State object needs to be fetched from stateIndexMap.
          // Send original state number (Example: int originalState = stateMapping().applyAsInt(stateNumber);)
          double val = rewardGenerator.transitionReward(stateIndexMap.get(state), choices.get(action).label()) // Action.label() returns label
                  + getActionVal(state, choices.get(action).distribution()); // Action.distribution returns distribution
          if (val > maxActionValue) {
            maxActionValue = val;
          }
        }
        values.put(state, maxActionValue);
        diff[state] = maxActionValue - oldValues.get(state);
      }
      iterCount++;

      max=0.0;
      min=Double.MAX_VALUE;
      // finding max and min can be done in log n time; we should have a better implementation
      for (double v : diff) {
        if (v > max) {
          max = v;
        }
        if (v < min) {
          min = v;
        }
      }
    } while ((max-min) >= targetPrecision);  // stopping criterion of value iteration
    bounds = Bounds.of(min, max);
  }

  private double getActionVal(int state, Distribution distribution) {
    //int numSuccessors = distribution.size();
    double sum = 0.0;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
/*
      if (successor == state) {
        continue;
      }
*/
      double probability = entry.getDoubleValue();
      assert values.containsKey(successor);
      double successorVal = values.get(successor);
      sum = sum + probability * successorVal;
    }
    return sum;
  }


  public Bounds getBounds(){
    return bounds;
  }

  // Return values such that in future, value iteration can be continued from current state.
  public Int2DoubleOpenHashMap getValues(){
    return this.values;
  }
}
