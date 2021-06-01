package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.Iterator;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import prism.ModelType;
import prism.PrismException;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class OnDemandValueIterator<S, M extends Model> implements Iterator<S, M> {

  private final Explorer<S, M> explorer;
  private final UnboundedValues values;
  private final BoundedMecQuotient<M> boundedMecQuotient;

  private final int revisitThreshold;

  public OnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, int revisitThreshold) {
    this.explorer = explorer;
    this.values = values;
    this.revisitThreshold = revisitThreshold;
    this.boundedMecQuotient = createBoundedMecQuotient(explorer.model());
  }

  public BoundedMecQuotient<M> createBoundedMecQuotient(M model){

    return new BoundedMecQuotient<M>(model);

  }

  @Override
  public Explorer<S, M> explorer() {
    return null;
  }

  @Override
  public AnnotatedModel<M> model() {
    return null;
  }

  @Override
  public Bounds bounds(int state) {
    return null;
  }

  public void run() throws PrismException {

    int initialState = explorer.initialStates().stream().findFirst().orElse(-1);
    assert initialState!=-1: "Explorer has no initial state";
    int representative = boundedMecQuotient.representative(initialState);

    while(!values.isSolved(representative)) {
      if (sample(representative)) {
        representative = boundedMecQuotient.representative(initialState);
      }
    }

  }

  @SuppressWarnings("unchecked")
  private boolean sample(int initialState) throws PrismException {

    IntStack visitStack = (IntStack) new IntArrayList();
    int currentState = initialState;
    Int2IntOpenHashMap visitCounts = new Int2IntOpenHashMap();

    boolean updatedEC = false;

    while(true){

      assert explorer.isExploredState(currentState);

      currentState = values.sampleNextState(currentState, choices(currentState));

      visitStack.push(currentState);
      visitCounts.putIfAbsent(currentState, 1);
      visitCounts.addTo(currentState, 1);

      if(boundedMecQuotient.isSinkState(currentState)){
        break;
      }
      if(visitCounts.get(currentState)>=this.revisitThreshold){
        updatedEC = true;
        break;
      }

      if(!explorer.isExploredState(currentState)) {
        explorer.exploreState(currentState);
      }

    }

    if(updatedEC){
      handleComponents();
    }
    else{
      if(boundedMecQuotient.isUncertainState(visitStack.popInt())){
        int mecRepresentative = visitStack.popInt();
        assert boundedMecQuotient.representative(mecRepresentative)==mecRepresentative;

        NatBitSet mecStates = NatBitSets.copyOf(explorer.exploredStates().stream()
                .filter(s -> boundedMecQuotient.representative(s)==mecRepresentative)
                .collect(Collectors.toList()));
        Mec mec = Mec.create(explorer.model(), mecStates);

        Supplier<M> modelSupplier;

        ModelType modelType = explorer.model().getModelType();
        if(modelType==ModelType.MDP){
          modelSupplier = () -> (M) new MarkovDecisionProcess();
        }
        else{
          throw new UnsupportedOperationException("Only Markov Decision Processes are supported at the moment");
        }
        RestrictedModel<M> mecRestrictedModel = ModelBuilder.buildMecRestrictedModel(explorer().model(),
                modelSupplier, mec);
        // handle precomputed rewards a

      }
    }

    return updatedEC;

  }

  public void handleComponents(){

  }

  private List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    return boundedMecQuotient.getChoices(state);
  }

}
