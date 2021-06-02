package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.Iterator;
import de.tum.in.pet.sampler.UnboundedSampler;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.*;
import parser.State;
import prism.ModelType;
import prism.PrismException;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class OnDemandValueIterator<S, M extends Model> implements Iterator<S, M> {
  private static final Logger logger = Logger.getLogger(UnboundedSampler.class.getName());

  private final Explorer<S, M> explorer;
  private final UnboundedValues values;
  private final BoundedMecQuotient<M> boundedMecQuotient;
  private final RewardGenerator<State> rewardGenerator;

  private final int revisitThreshold;
  private boolean newStatesSinceCollapse = false;

  private final Int2ObjectOpenHashMap<Int2DoubleOpenHashMap> mecValueCache = new Int2ObjectOpenHashMap<>();

  private final MecComponentAnalyser mecAnalyser = new MecComponentAnalyser();

  public OnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, RewardGenerator<State> rewardGenerator, int revisitThreshold) {
    this.explorer = explorer;
    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.revisitThreshold = revisitThreshold;
    this.boundedMecQuotient = new BoundedMecQuotient<>(explorer.model());
  }

  @Override
  public Explorer<S, M> explorer() {
    return explorer;
  }

  @Override
  public AnnotatedModel<M> model() {
    IntSet exploredStates = new IntOpenHashSet(explorer.exploredStates());
    exploredStates.removeIf((int state) -> values.isUnknown(boundedMecQuotient.representative(state)));
    return new AnnotatedModel<>(explorer.model(), explorer::getState, exploredStates);
  }

  @Override
  public Bounds bounds(int state) {
    return values.bounds(state);
  }

  private void initSinkStates(){

    int plusState = boundedMecQuotient.getPlusState();
    int minusState = boundedMecQuotient.getMinusState();
    int uncertainState = boundedMecQuotient.getUncertainState();

    values.update(plusState, List.of());

    values.update(minusState, List.of());

    values.update(uncertainState, List.of());

  }

  public void run() throws PrismException {

    initSinkStates();

    int initialState = explorer.initialStates().stream().findFirst().orElse(-1);
    assert initialState!=-1: "Explorer has no initial state";
    int representative = boundedMecQuotient.representative(initialState);

    while(!values.isSolved(representative)) {
      if (sample(representative)) {
        representative = boundedMecQuotient.representative(initialState);
      }
    }

  }

  private boolean sample(int initialState) throws PrismException {

    IntStack visitStack = new IntArrayList();
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
        explore(currentState);
      }

    }

    if(updatedEC){
      handleComponents();
    }
    else{
      int sinkState = visitStack.popInt();
      if(boundedMecQuotient.isUncertainState(sinkState)){
        int mecRepresentative = visitStack.popInt();
        updateMec(mecRepresentative);
      }
    }

    while(!visitStack.isEmpty()){
      int state = visitStack.popInt();
      assert !boundedMecQuotient.isSinkState(state);
      values.update(state, choices(state));
    }

    return updatedEC;

  }

  @SuppressWarnings("unchecked")
  public void updateMec(int mecRepresentative){
    assert boundedMecQuotient.representative(mecRepresentative)==mecRepresentative;

    Bounds mecBounds = bounds(mecRepresentative);
    double targetPrecision = mecBounds.difference()/2;

    NatBitSet mecStates = NatBitSets.copyOf(explorer.exploredStates().stream()
            .filter(s -> boundedMecQuotient.representative(s)==mecRepresentative)
            .collect(Collectors.toList()));
    Mec mec = Mec.create(explorer.model(), mecStates);

    Supplier<M> modelSupplier;
    ModelType modelType = explorer.model().getModelType();
    if(modelType==ModelType.MDP){
      modelSupplier = () -> (M) new MarkovDecisionProcess();
    }
    else {
      throw new UnsupportedOperationException("Only Markov Decision Processes are supported at the moment");
    }
    RestrictedModel<M> mecRestrictedModel = ModelBuilder.buildMecRestrictedModel(explorer().model(),
            modelSupplier, mec);

    Int2DoubleOpenHashMap valueCache = mecValueCache.computeIfAbsent(mecRepresentative, s -> new Int2DoubleOpenHashMap());

    RestrictedMecValueIterator<M> valueIterator = new RestrictedMecValueIterator<>(mecRestrictedModel, targetPrecision, rewardGenerator, valueCache);

    valueIterator.run();

    Bounds newBounds = valueIterator.getBounds();
    boundedMecQuotient.updateStayAction(mecRepresentative, newBounds);

    valueCache = valueIterator.getValues();
    mecValueCache.put(mecRepresentative, valueCache);
  }

  public void handleComponents(){
    if(!newStatesSinceCollapse){
      return;
    }

    logger.log(Level.INFO, "Searching components");

    NatBitSet states = NatBitSets.copyOf(explorer.exploredStates());
    states.removeAll(boundedMecQuotient.removedStates());

    List<NatBitSet> components = mecAnalyser.findComponents(boundedMecQuotient, states);

    if(components.isEmpty()){
      return;
    }

    if (logger.isLoggable(Level.FINE)) {
      int count = components.stream().mapToInt(NatBitSet::size).sum();
      logger.log(Level.FINE, "Found {0} new components with {1} new states",
              new Object[] {components.size(), count});
    }

    IntList representatives = boundedMecQuotient.collapse(components);
    var collapseIterator = components.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while(collapseIterator.hasNext()){
      int representative = representativeIterator.nextInt();
      values.collapse(representative, choices(representative), collapseIterator.next());
      boundedMecQuotient.updateStayAction(representative, bounds(representative));
    }

  }

  private void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    assert !boundedMecQuotient.isSinkState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);
    values.explored(state);
  }

  private List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    assert !boundedMecQuotient.isSinkState(state);
    return boundedMecQuotient.getChoices(state);
  }

}
