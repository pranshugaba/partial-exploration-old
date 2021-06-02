package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.List;
import java.util.function.IntConsumer;

public class BoundedMecQuotient<M extends Model> extends CollapseView<M> {

  private final int plusState;
  private final int minusState;
  private final int uncertainState;

  private final Int2ObjectOpenHashMap<Distribution> stayActionMap = new Int2ObjectOpenHashMap<>();

  public BoundedMecQuotient(M model) {
    super(model);

    this.plusState = model.addState();
    this.minusState = model.addState();
    this.uncertainState = model.addState();

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

    DistributionBuilder builder = Distributions.defaultBuilder();
    builder.add(plusState, bounds.lowerBound());
    builder.add(minusState, 1-bounds.upperBound());
    builder.add(uncertainState, 1-bounds.difference());

    return builder.build();

  }

  @Override
  public IntList collapse(List<? extends IntSet> stateList){

    IntSet oldRepresentatives = stayActionMap.keySet();
    IntList newRepresentatives = super.collapse(stateList);

    NatBitSet removedRepresentatives = NatBitSets.copyOf(oldRepresentatives);

    newRepresentatives.forEach((IntConsumer) removedRepresentatives::remove);

    assert removedRepresentatives.size() == oldRepresentatives.size()-newRepresentatives.size();

    for (int removedRepresentative : removedRepresentatives) {
      stayActionMap.remove(removedRepresentative);

      Bounds representativeBound = getBoundsFromStayAction(stayActionMap.get(removedRepresentative));

      int representative = this.representative(removedRepresentative);
      Bounds oldRepresentativeBound = getBoundsFromStayAction(stayActionMap.get(representative));

      double newLowerBound = Math.max(representativeBound.lowerBound(), oldRepresentativeBound.lowerBound());
      double newUpperBound = Math.max(representativeBound.upperBound(), oldRepresentativeBound.upperBound());
      Bounds newRepresentativeBound = Bounds.of(newLowerBound, newUpperBound);

      updateStayAction(representative, newRepresentativeBound);

    }

    assert stayActionMap.size() == newRepresentatives.size();

    return newRepresentatives;
  }

  @Override
  public List<Distribution> getChoices(int representative){
    List<Distribution> choices = super.getChoices(representative);
    if(stayActionMap.containsKey(representative)){
      choices.add(stayActionMap.get(representative));
    }

    return choices;
  }

  private Bounds getBoundsFromStayAction(Distribution stayAction){
    double upperBound = 1-stayAction.get(minusState);
    double lowerBound = stayAction.get(plusState);

    return Bounds.of(lowerBound, upperBound);
  }

}
