package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import parser.State;

import java.util.List;

public class RestrictedMecValueIterator<M extends Model> {

  public final RestrictedModel<M> restrictedModel;
  public final double targetPrecision;
  public final Int2DoubleOpenHashMap values;  // map of states and total values; the average value can be obtained by dividing by iterCount
  public final RewardGenerator<State> rewardGenerator;
  private int iterCount;  // number of iterations in value iteration

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, RewardGenerator<State> rewardGenerator){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = new Int2DoubleOpenHashMap();
    this.rewardGenerator = rewardGenerator;
    this.iterCount = 0;
  }

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, RewardGenerator<State> rewardGenerator,
                                    Int2DoubleOpenHashMap values){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.iterCount = 0;
  }

  public void run(){
    int numStates = restrictedModel.model().getNumStates();
    double[] diff =new double[numStates];  // This array stores the difference of values for each state between two successive iterations.

    for (int state = 0; state < numStates; state++) {  // initialise hashmap values
      values.put(state, 0.0);
    }

    double max, min;
    do {
      Int2DoubleOpenHashMap oldValues = new Int2DoubleOpenHashMap(values);
      for (int state = 0; state < numStates; state++) {
        double maxActionValue = 0.0;
        List<Action> actions = restrictedModel.model().getActions(state);
        for (Action action : actions) {  // find the value of the state over all actions
          double val = rewardGenerator.transitionReward(new State(state), action) + getActionVal(state, action.distribution());
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
    } while ((max-min)/iterCount >= targetPrecision);  // stopping criterion of value iteration
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
      if (values.containsKey(successor)) {
        double successorVal = values.get(successor);
        sum = sum + probability * successorVal;
      }
    }
    return sum;
  }


  public Bounds getBounds(){
    return null;
  }

  // Return values such that in future, value iteration can be continued from current state.
  public Int2DoubleOpenHashMap getValues(){
    return this.values;
  }
}
