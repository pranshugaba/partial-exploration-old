package de.tum.in.pet.sampler;

import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.model.Model;
import de.tum.in.pet.values.Bounds;
import prism.PrismException;

public interface Sampler<S, M extends Model> {
  Explorer<S, M> explorer();

  AnnotatedModel<M> model();

  Bounds bounds(int state);

  void build() throws PrismException;
}
