package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import parser.State;

public class RestrictedMecValueIterator<M extends Model> {

  public final RestrictedModel<M> restrictedModel;
  public final double targetPrecision;
  public final Int2DoubleOpenHashMap values;
  public final RewardGenerator<State> rewardGenerator;

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, RewardGenerator<State> rewardGenerator){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = new Int2DoubleOpenHashMap();
    this.rewardGenerator = rewardGenerator;
  }

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, RewardGenerator<State> rewardGenerator,
                                    Int2DoubleOpenHashMap values){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;

  }

  public void run(){

  }

  public Bounds getBounds(){
    return null;
  }

  public Int2DoubleOpenHashMap getValues(){
    return this.values;
  }
}
