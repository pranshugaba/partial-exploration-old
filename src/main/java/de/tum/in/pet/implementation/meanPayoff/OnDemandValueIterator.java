package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.Iterator;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.*;
import parser.State;
import prism.PrismException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// This class implements the OnDemand VI Algorithm from the CAV'17 paper.
public class OnDemandValueIterator<M extends Model> implements Iterator<State, M> {
  private static final Logger logger = Logger.getLogger(OnDemandValueIterator.class.getName());

  private final Explorer<State, M> explorer;
  private final UnboundedValues values;
  private final BoundedMecQuotient<M> boundedMecQuotient;
  private final RewardGenerator<State> rewardGenerator;

  private final int revisitThreshold;
  private final double rMax;
  // variable keeps track if new states have been explored and if handleComponents() needs to be run again.
  private boolean newStatesSinceCollapse = false;

  // stores most recent VI results for all states.
  private final Int2ObjectOpenHashMap<Int2DoubleOpenHashMap> mecValueCache = new Int2ObjectOpenHashMap<>();

  private final MecComponentAnalyser mecAnalyser = new MecComponentAnalyser();

  public OnDemandValueIterator(Explorer<State, M> explorer, UnboundedValues values, RewardGenerator<State> rewardGenerator, int revisitThreshold, double rMax) {
    this.explorer = explorer;

    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.revisitThreshold = revisitThreshold;
    this.boundedMecQuotient = new BoundedMecQuotient<>(explorer.model());
    this.rMax = rMax;
  }

  @Override
  public Explorer<State, M> explorer() {
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

  // Initialize Sink State bounds.
  private void initSinkStates(){

    int plusState = boundedMecQuotient.getPlusState();
    int minusState = boundedMecQuotient.getMinusState();
    int uncertainState = boundedMecQuotient.getUncertainState();

    values.update(plusState, List.of(Distributions.singleton(Integer.MAX_VALUE, 1d)));
    assert values.bounds(plusState).lowerBound()==1;

    values.update(minusState, List.of());
    assert values.bounds(minusState).upperBound()==0;

    assert values.bounds(uncertainState).equals(Bounds.reachUnknown());

  }

  public void run() throws PrismException {

    initSinkStates();

    logger.log(Level.INFO, "Initialized Sink States.");

    int run = 0;

    int initialState = explorer.initialStates().stream().findFirst().orElse(-1);
    assert initialState!=-1: "Explorer has no initial state";
    int representative = boundedMecQuotient.representative(initialState);

    // isSolved() defined in UnboundedReachValues
    while(!values.isSolved(representative)) {  // The values between upper and lower bounds for the initial states should,be less than epsilon
      logger.log(Level.INFO, "Run "+run);
      if (sample(representative)) {
        // initialState may be part of an MEC and the MEC may be collapsed, and we may have a representative that is different
        // from initialState
        representative = boundedMecQuotient.representative(initialState);
      }
      run++;  // count of episodic runs
    }

  }

  private boolean sample(int initialState) throws PrismException {

    IntStack visitStack = new IntArrayList();
    int currentState = initialState;
    Int2IntOpenHashMap stateVisitCounts = new Int2IntOpenHashMap();  // keeps counts of the number of times a state is visited

    boolean updatedEC = false;

    while(true){

      visitStack.push(currentState);
      stateVisitCounts.putIfAbsent(currentState, 0);
      stateVisitCounts.addTo(currentState, 1);

      if(boundedMecQuotient.isSinkState(currentState)){
        break;
      }
      if(stateVisitCounts.get(currentState)>=this.revisitThreshold){
        updatedEC = true;
        break;
      }

      if(!explorer.isExploredState(currentState)) {
        explore(currentState);  // action choices etc. are populated in the partial model. The bounds of currentState are also initialised.
      }

      assert explorer.isExploredState(currentState);

      currentState = values.sampleNextState(currentState, choices(currentState));

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

    // update the values of the states along the path that is stored in visitStack (Line 18--21 of OnDemandVI algorithm from CAV'17 paper)
    while(!visitStack.isEmpty()){
      // In a path there can be at most one sink state at the end. If such a sink state appears in the path, then it would have already
      // been popped inside the else block above.
      int state = boundedMecQuotient.representative(visitStack.popInt());
      assert !boundedMecQuotient.isSinkState(state);
      values.update(state, choices(state));
    }

    return updatedEC;

  }

  // Implements lines 11-15 in the paper. Runs VI on mec.
  public void updateMec(int mecRepresentative){
    assert boundedMecQuotient.representative(mecRepresentative)==mecRepresentative;

    logger.log(Level.INFO, "updating MEC");

    Bounds mecBounds = bounds(mecRepresentative);
    double targetPrecision = mecBounds.difference()/2;

    // get all the MEC states corresponding to mecRepresentative.
    NatBitSet mecStates = NatBitSets.copyOf(explorer.exploredStates().stream()
            .filter(s -> boundedMecQuotient.representative(s)==mecRepresentative)
            .collect(Collectors.toList()));
    Mec mec = Mec.create(explorer.model(), mecStates);

    // Fetch the precomputed value map of the mec from the cache, and if there is not any, then returns an empty map.
    // the key of the map is mecRepresentative.
    Int2DoubleOpenHashMap valueCache = mecValueCache.computeIfAbsent(mecRepresentative, s -> new Int2DoubleOpenHashMap());

    Int2ObjectFunction<State> stateIndexMap = explorer::getState; // lambda function that returns a state object when given the state index. required for accessing reward generator function.

    RestrictedMecValueIterator<M> valueIterator = new RestrictedMecValueIterator<>(this.explorer.model(), mec, targetPrecision, rewardGenerator, stateIndexMap, valueCache);

    valueIterator.run();

    Bounds newBounds = valueIterator.getBounds();
    Bounds scaledBounds = Bounds.of(newBounds.lowerBound()/this.rMax, newBounds.upperBound()/this.rMax);
    boundedMecQuotient.updateStayAction(mecRepresentative, scaledBounds);

    valueCache = valueIterator.getValues();
    mecValueCache.put(mecRepresentative, valueCache);
  }

  // Implements OnTheFlyEC from paper.
  public void handleComponents(){
    if(!newStatesSinceCollapse){
      return;
    }

    newStatesSinceCollapse = false;
    logger.log(Level.INFO, "Searching components");

    NatBitSet states = NatBitSets.copyOf(explorer.exploredStates());
    states.removeAll(boundedMecQuotient.removedStates()); // states variable now stores only the states in the current collapsed partial model.

    List<NatBitSet> components = mecAnalyser.findComponents(boundedMecQuotient, states);  // find all MECs in the partial model.

    if(components.isEmpty()){
      return;
    }

    if (logger.isLoggable(Level.FINE)) {
      int count = components.stream().mapToInt(NatBitSet::size).sum();
      logger.log(Level.FINE, "Found {0} new components with {1} new states",
              new Object[] {components.size(), count});
    }

    IntList representatives = boundedMecQuotient.collapse(components);  // the stay action is added here.
    var collapseIterator = components.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while(collapseIterator.hasNext()){
      // the components and the corresponding representatives are in the same order.
      int representative = representativeIterator.nextInt();
      // updates the bounds of the representative according to all actions of mec members going out of the MEC.
      values.collapse(representative, choices(representative), collapseIterator.next());
    }

  }

  // Add state to partial model. Initialize bounds.
  private void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    assert !boundedMecQuotient.isSinkState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);  //  state added to partial model, and explorer.isExploredState(state) is set to true.
    values.explored(state);  // the bounds are initialised
  }

  private List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    assert !boundedMecQuotient.isSinkState(state);
    return boundedMecQuotient.getChoices(state);
  }

}
