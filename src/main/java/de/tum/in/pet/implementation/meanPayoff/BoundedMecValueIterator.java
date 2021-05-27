package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;

import java.util.HashMap;

public class BoundedMecValueIterator<M extends Model> implements ValueIterator{

  public final RestrictedModel<M> restrictedModel;
  public final float targetPrecision;
  public final HashMap<Integer, Integer> values;

  public BoundedMecValueIterator(RestrictedModel<M> restrictedModel, float targetPrecision, HashMap<Integer, Integer> values){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = values;
  }

  @Override
  public void run() {

  }

  @Override
  public boolean stoppingCriterion() {
    return false;
  }

  @Override
  public void getResult() {

  }
}
