package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;

import java.util.HashMap;

public class RestrictedMecValueIterator<M extends Model> {

  public final RestrictedModel<M> restrictedModel;
  public final double targetPrecision;
  public final HashMap<Integer, Integer> values;

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = new HashMap<>();
  }

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, HashMap<Integer, Integer> values){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = values;
  }

  public void run(){

  }

  public Bounds getBounds(){
    return null;
  }
}
