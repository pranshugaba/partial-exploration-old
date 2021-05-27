package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.probmodels.model.CollapseView;
import de.tum.in.probmodels.model.Model;

public class BoundedMecQuotient<M extends Model> extends CollapseView<M> {
  public BoundedMecQuotient(M model) {
    super(model);
  }
}
