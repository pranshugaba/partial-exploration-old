package de.tum.in.pet.util;

import de.tum.in.pet.model.Model;
import de.tum.in.pet.sampler.UnboundedSampler;
import de.tum.in.pet.util.annotation.Tuple;
import de.tum.in.pet.values.StateUpdate;
import de.tum.in.pet.values.StateValues;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class SamplingInstance<M extends Model> {
  public abstract UnboundedSampler<M> sampler();

  public abstract StateValues stateValues();

  public abstract StateUpdate stateUpdate();


  public static <M extends Model> SamplingInstance<M> of(UnboundedSampler<M> sampler,
      StateValues stateValues, StateUpdate stateUpdate) {
    return SamplingInstanceTuple.create(sampler, stateValues, stateUpdate);
  }
}
