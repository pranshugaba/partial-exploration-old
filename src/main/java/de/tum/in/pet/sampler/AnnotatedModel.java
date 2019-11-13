package de.tum.in.pet.sampler;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.probmodels.model.Model;
import explicit.ModelExplicit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import parser.State;

public class AnnotatedModel<M extends Model> {
  public final M model;
  public final NatBitSet exploredStates;
  private NatBitSet fringeStates;

  public AnnotatedModel(M model, IntFunction<?> stateToIndex, NatBitSet exploredStates) {
    this.model = model;
    this.exploredStates = NatBitSets.compact(exploredStates);

    if (model instanceof ModelExplicit) {
      // TODO HACK
      int numStates = model.getNumStates();
      List<State> stateList = new ArrayList<>(numStates);
      for (int i = 0; i < numStates; i++) {
        Object state = stateToIndex.apply(i);
        if (state instanceof State) {
          stateList.add((State) state);
        } else {
          break;
        }
      }
      ((ModelExplicit) model).setStatesList(stateList);
    }
  }

  public NatBitSet getFringeStates() {
    if (fringeStates == null) {
      int numStates = model.getNumStates();
      NatBitSet fringeStates = NatBitSets.modifiableCopyOf(exploredStates, numStates + 1);
      fringeStates.flip(0, numStates);
      this.fringeStates = NatBitSets.compact(fringeStates);
    }
    return fringeStates;
  }
}
