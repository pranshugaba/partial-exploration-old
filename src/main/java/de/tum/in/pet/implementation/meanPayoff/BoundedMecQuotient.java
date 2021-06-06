package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

public class BoundedMecQuotient<M extends Model> extends CollapseView<M> {

  private final int plusState;
  private final int minusState;
  private final int uncertainState;

  private final Int2ObjectOpenHashMap<Distribution> stayActionMap = new Int2ObjectOpenHashMap<>();

  public BoundedMecQuotient(M model) {
    super(model);

    this.plusState = -1;
    this.minusState = -2;
    this.uncertainState = -3;

  }

  public int getPlusState() {
    return plusState;
  }

  public int getMinusState() {
    return minusState;
  }

  public int getUncertainState() {
    return uncertainState;
  }

  public boolean isSinkState(int state){
    return state==this.plusState||state==this.minusState
            ||state==this.uncertainState;
  }

  public boolean isUncertainState(int state){
    return state==this.uncertainState;
  }

  public void updateStayAction(int representative, Bounds bounds) {

    Distribution distribution = getStayDistribution(bounds);
    stayActionMap.put(representative, distribution);

  }

  public Distribution getStayDistribution(Bounds bounds){

    assert bounds.lowerBound()<=1 && bounds.upperBound()<=1;
    assert bounds.lowerBound()>=0 && bounds.upperBound()>=0;

    DistributionBuilder builder = Distributions.defaultBuilder();
    if(bounds.lowerBound()>0) {
      builder.add(plusState, bounds.lowerBound());
    }
    if(1-bounds.upperBound()>0) {
      builder.add(minusState, 1-bounds.upperBound());

    }
    if(bounds.difference()>0) {
      builder.add(uncertainState, bounds.difference());
    }

    return builder.build();

  }

  @Override
  public IntList collapse(List<? extends IntSet> stateList){

    // The key set of the stayActionMap should have an old list of representatives
    IntSet oldRepresentatives = stayActionMap.keySet();
    // New set of representatives
    IntList newRepresentatives = super.collapse(stateList);

    // These 2 operations give removedRepresentatives = oldRepresentatives-newRepresentatives
    NatBitSet removedRepresentatives = NatBitSets.copyOf(oldRepresentatives);
    newRepresentatives.forEach((IntConsumer) removedRepresentatives::remove);

    // For every group, we need to have only one stayAction to update. However, the collapse operation may create groups
    // with more than one stayActions as it may now have more than 1 members from oldRepresentatives. This loop merges
    // the stay actions for such groups.
    for (int removedRepresentative : removedRepresentatives) {
      Bounds removedRepresentativeBound = getBoundsFromStayAction(stayActionMap.get(removedRepresentative));
      // Removing stay action of removed representative
      stayActionMap.remove(removedRepresentative);

      // Representative of group to which removedRepresentative now belongs
      int representative = this.representative(removedRepresentative);
      Bounds representativeBound = getBoundsFromStayAction(stayActionMap.get(representative));

      // Creating new bounds based on both stayAction bounds.
      double newLowerBound = Math.max(removedRepresentativeBound.lowerBound(), representativeBound.lowerBound());
      double newUpperBound = Math.max(removedRepresentativeBound.upperBound(), representativeBound.upperBound());
      Bounds newRepresentativeBound = Bounds.of(newLowerBound, newUpperBound);

      // Update stayAction of group
      updateStayAction(representative, newRepresentativeBound);

    }

    for(int i: newRepresentatives){
      // The below condition will be true when we collapse a new group with zero members in removedRepresentatives.
      // For such a group, l = 0, u = 1
      if(!stayActionMap.containsKey(i)){
        updateStayAction(i, Bounds.reachUnknown());
      }
    }

    assert stayActionMap.size() == newRepresentatives.size();

    return newRepresentatives;
  }

  @Override
  public List<Distribution> getChoices(int representative){
    // Get the choices from the collapse model
    List<Distribution> choices = new ArrayList<>(super.getChoices(representative));
    // Adding the stay action if there is one associated with representative
    if(stayActionMap.containsKey(representative)){
      choices.add(stayActionMap.get(representative));
    }

    return Collections.unmodifiableList(choices);
  }

  // Calculates upper and lower bound for a state from it's stayAction distribution
  private Bounds getBoundsFromStayAction(Distribution stayAction){
    double upperBound = 1-stayAction.get(minusState);
    double lowerBound = stayAction.get(plusState);

    assert upperBound>=lowerBound;
    assert upperBound<=1 && lowerBound<=1;
    assert upperBound>=0 && lowerBound>=0;

    return Bounds.of(lowerBound, upperBound);
  }

}
