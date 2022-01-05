package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.ints.*;


public class RestrictedMecValueIterator<S, M extends Model> {

  public final Mec mec; // Mec with respect to original model
  public final double targetPrecision;
  public final Int2DoubleMap values;  // map of states and total values; the average value can be obtained by dividing by iterCount
  public final RewardGenerator<S> rewardGenerator;
  private int iterCount;  // number of iterations in value iteration
  private final Int2ObjectFunction<S> stateIndexMap; // map from original model state number to corresponding state object
  private final double rMax;
  private final long timeout;

  private Bounds bounds;

  // self loops are induced in the model for every action, and the self loop transition is chosen with probability 1-self.aperiodictyConstant
  // this helps in ensuring the convergence of the algorithm in a finite number of steps. Increasing the constant gives a more precise value.
  // however, it takes a larger number of steps. This is primarily required when considering periodic Models. this process makes the model aperiodic.
  // Refer to Puterman '94 Section 8.5.4 for details and proofs.
  private final double aperidocityConstant;

  // Returns the distribution for a state x and it's corresponding action index y
  private Int2ObjectFunction<Int2ObjectFunction<Distribution>> distributionFunction = x -> (y -> null);

  // Returns the label of the action y
  private Int2ObjectFunction<Int2ObjectFunction<Object>> labelFunction = x -> (y -> null);

  public RestrictedMecValueIterator(Mec mec, double targetPrecision, RewardGenerator<S> rewardGenerator,
                                    Int2ObjectFunction<S> stateIndexMap, double rMax, long timeout){
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.timeout = timeout;
    this.values = new Int2DoubleOpenHashMap();
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
    this.aperidocityConstant = 0.8;
    this.rMax = rMax;
  }

  public RestrictedMecValueIterator(Mec mec, double targetPrecision, RewardGenerator<S> rewardGenerator,
                                    Int2ObjectFunction<S> stateIndexMap, Int2DoubleMap values, double rMax, long timeout){
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.timeout = timeout;
    this.iterCount = 0;
    this.aperidocityConstant = 0.8;
    this.rMax = rMax;
  }

  // todo: confidence width
  /**
   * Simulates VI.
   */
  public void run(){
    int numStates = mec.size();
    NatBitSet states = mec.states;
    double[] diff =new double[numStates];  // This array stores the difference of values for each state between two successive iterations. This is Delta_n in CAV'17.

    if(values.size()==0) { // no pre-computed values sent
      IntIterator stateIterator = states.iterator();
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        values.put(state, 0.0);
      }
    }

    double max, min;
    do {
      Int2DoubleOpenHashMap oldValues = new Int2DoubleOpenHashMap(values);
      Int2DoubleOpenHashMap tempValues = new Int2DoubleOpenHashMap();
      IntIterator stateIterator = states.iterator();
      int count = 0;
      // A single iteration of VI
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        double maxActionValue = 0.0;
        IntSet allowedActions = mec.actions.get(state);  // allowedActions numbered as in original model
        assert allowedActions != null;
        // Get Actions from original model, filter according to mec actions
        for (int action : allowedActions) {  // find the value of the state over all actions
          // Send action label instead of action object. State object needs to be fetched from stateIndexMap.
          // val_transformed = const*rewards + actionVal. Instead, we have found val = rewards + actionVal/const (This division is done by actionVal itself). We do this to store the original value.
          double val = rewardGenerator.transitionReward(stateIndexMap.get(state), labelFunction.apply(state).apply(action)) // Action.label() returns label
                  + rewardGenerator.stateReward(stateIndexMap.get(state))
                  + getActionVal(state, distributionFunction.apply(state).apply(action)); // Action.distribution returns distribution
          if (val > maxActionValue) {
            maxActionValue = val;
          }
        }
        tempValues.put(state, maxActionValue);
        diff[count++] = (maxActionValue - oldValues.get(state));
      }
      for(int state: values.keySet()) {
        values.put(state, tempValues.get(state));
      }
      iterCount++;

      max=0.0; // max of diff (max of Delta_n)
      min=Double.MAX_VALUE; // min of diff (min of Delta_n)
      for (double v : diff) {
        if (v > max) {
          max = v;
        }
        if (v < min) {
          min = v;
        }
      }
    } while ((max-min) >= targetPrecision && (!isTimeout()));  // stopping criterion of value iteration


    // Sometimes the upper bound is slightly greater than rMax, because of floating point error.
    // This was observed when running the pnueli-zuck3 model.
    // We change the upper bound to be rMax itself, when it goes beyond rMax.
    if (max >= rMax) {
      max = rMax;
    }
    bounds = Bounds.of(min, max);
  }

  // todo: vectorize operation

  /**
   * @param state: Integer value of the state from which the action originates.
   * @param distribution: Distribution consisting of the probability of reaching successor states for the action.
   * @return Returns the associated value of a single action.
   */
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
      double probability = this.aperidocityConstant*entry.getDoubleValue();
      assert values.containsKey(successor);
      // this gives transformed value of successor
      double successorVal = this.aperidocityConstant*values.get(successor);
      sum = sum + probability * successorVal;
    }
    sum += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state);
    // sum stores the transformed value. This returns the original value
    return sum/this.aperidocityConstant;
  }


  public void setDistributionFunction(Int2ObjectFunction<Int2ObjectFunction<Distribution>> distributionFunction) {
    this.distributionFunction = distributionFunction;
  }

  public void setLabelFunction(Int2ObjectFunction<Int2ObjectFunction<Object>> labelFunction) {
    this.labelFunction = labelFunction;
  }


  /**
   * @return Returns the calculated reward upper and lower bounds.
   */
  public Bounds getBounds(){
    return bounds;
  }

  /**
   * @return Return values such that in future, value iteration can be continued from current values.
   */
  public Int2DoubleMap getValues(){
    return this.values;
  }

  private boolean isTimeout() {
    return System.currentTimeMillis() >= timeout;
  }
}
