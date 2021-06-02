package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;
import explicit.rewards.MDPRewards;
import explicit.rewards.MDPRewardsSimple;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.HashMap;

public class RestrictedMecValueIterator<M extends Model> {

  public final RestrictedModel<M> restrictedModel;
  public final double targetPrecision;
  public final Int2DoubleOpenHashMap values;
  public final MDPRewards mdpRewards;

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, MDPRewards originalRewards){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = new Int2DoubleOpenHashMap();
    this.mdpRewards = restrictedModel.buildMdpRewards(originalRewards);
  }

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, MDPRewards originalRewards,
                                    Int2DoubleOpenHashMap values){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.mdpRewards = restrictedModel.buildMdpRewards(originalRewards);

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
