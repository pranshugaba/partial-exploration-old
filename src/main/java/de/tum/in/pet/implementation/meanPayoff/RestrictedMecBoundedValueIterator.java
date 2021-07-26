package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.ints.*;

import java.util.List;

public class RestrictedMecBoundedValueIterator<S, M extends Model> {

  public final M model; // Original Model
  public final Mec mec; // Mec with respect to original model
  public final double targetPrecision;
  public final Int2ObjectMap<Bounds> values;  // map of states and total values; the average value can be obtained by dividing by iterCount
  public final RewardGenerator<S> rewardGenerator;
  private int iterCount;  // number of iterations in value iteration
  private final Int2ObjectFunction<S> stateIndexMap; // map from original model state number to corresponding state object

  private Bounds bounds;

  // Returns the confidence width for a state x and it's corresponding action index y
  private Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction = x -> (y -> (0));

  private final double aperidocityConstant;

  public RestrictedMecBoundedValueIterator(M model, Mec mec, double targetPrecision, RewardGenerator<S> rewardGenerator,
                                    Int2ObjectFunction<S> stateIndexMap){
    this.model = model;
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = new Int2ObjectOpenHashMap<>();
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
    this.aperidocityConstant = 0.8;
  }

  public RestrictedMecBoundedValueIterator(M model, Mec mec, double targetPrecision, RewardGenerator<S> rewardGenerator,
                                    Int2ObjectFunction<S> stateIndexMap, Int2ObjectMap<Bounds> values){
    this.model = model;
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
    this.aperidocityConstant = 0.8;
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

  public void run(){
    int numStates = mec.size();
    NatBitSet states = mec.states;
    Bounds[] diff =new Bounds[numStates];  // This array stores the difference of values for each state between two successive iterations. This is Delta_n in CAV'17.

    if(values.size()==0) { // no pre-computed values sent
      IntIterator stateIterator = states.iterator();
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        values.put(state, Bounds.of(0, 0));
      }
    }

    double maxUpper, minUpper, maxLower, minLower;
    do {
      Int2ObjectMap<Bounds> oldValues = new Int2ObjectOpenHashMap<>(values);
      Int2ObjectMap<Bounds> tempValues = new Int2ObjectOpenHashMap<>();
      IntIterator stateIterator = states.iterator();
      int count = 0;
      // A single iteration of VI
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        double maxUpperBound = 0.0;
        double maxLowerBound = 0.0;
        IntSet allowedActions = mec.actions.get(state);  // allowedActions numbered as in original model
        assert allowedActions != null;
        List<Action> choices = model.getActions(state);  // get all actions (not distributions)
        // Get Actions from original model, filter according to mec actions
        for (int action : allowedActions) {  // find the value of the state over all actions
          // Send action label instead of action object. State object needs to be fetched from stateIndexMap.
          // val_transformed = const*rewards + actionVal. Instead, we have found val = rewards + actionVal/const (This division is done by actionVal itself). We do this to store the original value.
          double val = rewardGenerator.transitionReward(stateIndexMap.get(state), choices.get(action).label()) // Action.label() returns label
                  + rewardGenerator.stateReward(stateIndexMap.get(state));

          Bounds actionBounds = getActionBounds(state, choices.get(action).distribution(), // Action.distribution returns distribution
                  confidenceWidthFunction.get(state).get(action));
          maxLowerBound = Math.max(actionBounds.lowerBound()+val, maxLowerBound);
          maxUpperBound = Math.max(actionBounds.upperBound()+val, maxUpperBound);
        }
        tempValues.put(state, Bounds.of(maxLowerBound, maxUpperBound));
        diff[count++] = Bounds.of(maxLowerBound-oldValues.get(state).lowerBound(),
                maxUpperBound-oldValues.get(state).upperBound());
      }
      for(int state: values.keySet()) {
        values.put(state, tempValues.get(state));
      }
      iterCount++;

      maxLower=0.0; // max of diff (max of Delta_n)
      minLower=Double.MAX_VALUE; // min of diff (min of Delta_n)
      maxUpper=0.0; // max of diff (max of Delta_n)
      minUpper=Double.MAX_VALUE; // min of diff (min of Delta_n)
      for (Bounds b : diff) {
        maxLower = Math.max(maxLower, b.lowerBound());
        minLower = Math.min(minLower, b.lowerBound());
        maxUpper = Math.max(maxUpper, b.upperBound());
        minUpper = Math.min(minUpper, b.upperBound());
      }
      int a = 0;
    } while ((maxLower-minLower) >= targetPrecision && (maxUpper-minUpper) >= targetPrecision);  // stopping criterion of value iteration
    bounds = Bounds.of(minLower, maxUpper);
  }

  // todo: vectorize operation
  private Bounds getActionBounds(int state, Distribution distribution, double confidenceWidth) {
    double lower = 0.0d;
    double upper = 0.0d;
    double probSum = 0.0d;
    double minLower = Integer.MAX_VALUE;
    double maxUpper = 0;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      double probability = Math.max(0, entry.getDoubleValue()-confidenceWidth);
      Bounds succValues = values.get(successor);
      probSum += probability;
      double succLower = this.aperidocityConstant*succValues.lowerBound();
      double succUpper = this.aperidocityConstant*succValues.upperBound();
      lower += succLower*probability*this.aperidocityConstant;
      upper += succUpper*probability*this.aperidocityConstant;
      minLower = Math.min(minLower, succLower);
      maxUpper = Math.max(maxUpper, succUpper);
    }
    double remProb = 1-probSum;
    lower += remProb*minLower*this.aperidocityConstant;
    upper += remProb*maxUpper*this.aperidocityConstant;
    lower += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state).lowerBound();
    upper += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state).upperBound();
    return Bounds.of(lower/this.aperidocityConstant, upper/this.aperidocityConstant);
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
  public Int2ObjectMap<Bounds> getValues(){
    return this.values;
  }

}
