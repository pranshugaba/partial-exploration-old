package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.probmodels.model.Model;

public class OnDemandValueIterator<M extends Model> implements ValueIterator{

  private final M model;
  private final UnboundedValues values;

  public OnDemandValueIterator(M model, UnboundedValues values) {
    this.model = model;
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
