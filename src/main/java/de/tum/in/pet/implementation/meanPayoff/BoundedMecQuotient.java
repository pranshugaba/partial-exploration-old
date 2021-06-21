package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

// Class to represent the Bounded MEC Quotient of a partially explored model. Please make sure that the number of states
// is less than or equal to Integer.MAX_VALUE-3.
public class BoundedMecQuotient<M extends Model> extends CollapseView<M> {

  private final int plusState;
  private final int minusState;
  private final int uncertainState;

  private final Int2ObjectOpenHashMap<Distribution> stayActionMap = new Int2ObjectOpenHashMap<>();

  public BoundedMecQuotient(M model) {
    super(model);

    this.plusState = Integer.MAX_VALUE;
    this.minusState = Integer.MAX_VALUE-1;
    this.uncertainState = Integer.MAX_VALUE-2;

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

    // Note that oldRepresentatives may also contain representatives that are not a part of the stateList. These
    // maybe previously collapsed components' representatives. The below if condition should filter out such
    // representatives from removedRepresentatives.
    for (int oldRepresentative : oldRepresentatives) {
      if (oldRepresentative==representative(oldRepresentative)){
        removedRepresentatives.remove(oldRepresentative);
      }
    }

    for(int i: newRepresentatives){
      // The below condition will be true when we collapse a new group with zero members in removedRepresentatives.
      // For such a group, l = 0, u = 1
      if(!stayActionMap.containsKey(i)){
        updateStayAction(i, Bounds.reachUnknown());
      }
    }

    // For every group, we need to have only one stayAction to update. However, the collapse operation may create groups
    // with more than one stayActions as it may now have more than 1 members from oldRepresentatives. This loop merges
    // the stay actions for such groups.
    for (int removedRepresentative : removedRepresentatives) {
      Bounds removedRepresentativeBound = getBoundsFromStayAction(stayActionMap.get(removedRepresentative));

      // it can be the case that "stateList" only consists of newComponents.
      if (this.representative(removedRepresentative)!=removedRepresentative) {
        // Removing stay action of removed representative
        stayActionMap.remove(removedRepresentative);
      }
      else{
        continue;
      }

      // Representative of group to which removedRepresentative now belongs
      int representative = this.representative(removedRepresentative);
      assert representative!=removedRepresentative;

      Bounds representativeBound = getBoundsFromStayAction(stayActionMap.get(representative));

      // Creating new bounds based on both stayAction bounds.
      double newLowerBound = Math.max(removedRepresentativeBound.lowerBound(), representativeBound.lowerBound());
      double newUpperBound = Math.max(removedRepresentativeBound.upperBound(), representativeBound.upperBound());
      Bounds newRepresentativeBound = Bounds.of(newLowerBound, newUpperBound);

      // Update stayAction of group
      updateStayAction(representative, newRepresentativeBound);

    }

    return newRepresentatives;
  }

  @Override
  public List<Distribution> getChoices(int state){
    // Get the choices from the collapse model
    List<Distribution> choices;

    // CollapseView.getChoices() returns all actions from a state, but the self-loops. This may produce deadlock states
    // and ignore several MECs(eg. states with nothing but self loops). This can be bad for reward calculation.
    if(stayActionMap.containsKey(state)) {
      // if a state is a key in stayActionMap, it must be a representative of a few collapsed states.
      // if the state is a representative of collapsed states, we don't need to take care of self-loops. We have already
      // taken care of the rewards for these states during VI.
      choices = new ArrayList<>(super.getChoices(state));
      // Adding the stay action if there is one associated with representative
      choices.add(stayActionMap.get(state));
    }
    else{
      // for states that aren't collapsed yet, we need to fetch the distributions directly from the model.
      // The Model.getChoices() function doesn't ignore self loops.
      choices = getModel().getChoices(state);
      List<Distribution> transformedChoices = new ArrayList<>();

      IntUnaryOperator map = this::representative;
      Predicate<Distribution> unchanged = d -> d.support().stream().noneMatch(this::isRemoved);

      for (Distribution choice : choices) {
        if (!unchanged.test(choice)) {
          DistributionBuilder builder = choice.map(map);
          choice = builder.build();
        }

        transformedChoices.add(choice);
      }

      choices = transformedChoices;
    }

    return choices;
  }

  // Calculates upper and lower bound for a state from it's stayAction distribution
  public Bounds getBoundsFromStayAction(Distribution stayAction){
    double upperBound = 1-stayAction.get(minusState);
    double lowerBound = stayAction.get(plusState);

    assert upperBound>=lowerBound;
    assert upperBound<=1 && lowerBound<=1;
    assert upperBound>=0 && lowerBound>=0;

    return Bounds.of(lowerBound, upperBound);
  }

  public Distribution getStayAction(int representative){
    assert stayActionMap.containsKey(representative);

    return stayActionMap.get(representative);
  }

}
