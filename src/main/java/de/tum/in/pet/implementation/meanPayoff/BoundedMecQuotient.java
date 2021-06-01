package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.probmodels.model.CollapseView;
import de.tum.in.probmodels.model.Model;

public class BoundedMecQuotient<M extends Model> extends CollapseView<M> {

  private int plusState;
  private int minusState;
  private int uncertainState;

  public BoundedMecQuotient(M model) {
    super(model);

    this.plusState = model.addState();
    this.minusState = model.addState();
    this.uncertainState = model.addState();

  }

  public boolean isSinkState(int state){
    return state==this.plusState||state==this.minusState
            ||state==this.uncertainState;
  }

  public boolean isUncertainState(int state){
    return state==this.uncertainState;
  }

}
