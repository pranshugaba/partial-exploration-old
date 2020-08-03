package de.tum.in.pet.sampler;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.model.Model;
import prism.PrismException;

public interface Iterator<S, M extends Model> {
  Explorer<S, M> explorer();

  AnnotatedModel<M> model();

  Bounds bounds(int state);

  void run() throws PrismException;
}
