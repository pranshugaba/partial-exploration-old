package de.tum.in.pet.sampler;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.probmodels.model.Model;
import explicit.ModelExplicit;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import parser.State;

public class AnnotatedModel<M extends Model> {
  public final M model;
  public final IntSet exploredStates;
  private IntSet fringeStates;

  public AnnotatedModel(M model, IntFunction<?> stateToIndex, IntSet exploredStates) {
    this.model = model;
    this.exploredStates = IntSets.unmodifiable(exploredStates);

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

  public IntSet getFringeStates() {
    if (fringeStates == null) {
      int numStates = model.getNumStates();
      NatBitSet fringeStates = NatBitSets.copyOf(exploredStates);
      fringeStates.flip(0, numStates);
      this.fringeStates = NatBitSets.compact(fringeStates);
    }
    return fringeStates;
  }
}
