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
import prism.PrismException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.tum.in.probmodels.util.Util.isZero;

// This class implements the OnDemand VI Algorithm from the CAV'17 paper.
public class OnDemandValueIterator<S, M extends Model> implements Iterator<S, M> {
  protected static final Logger logger = Logger.getLogger(OnDemandValueIterator.class.getName());

  protected final Explorer<S, M> explorer;
  protected final UnboundedValues values;
  private final BoundedMecQuotient<M> boundedMecQuotient;
  protected final RewardGenerator<S> rewardGenerator;

  protected final int revisitThreshold;
  protected final double rMax;

  // variable keeps track if new states have been explored and if handleComponents() needs to be run again.
  protected boolean newStatesSinceCollapse = false;

  // stores most recent VI results for all states.
  protected Int2ObjectMap<Int2DoubleMap> mecValueCache = new Int2ObjectOpenHashMap<>();

  protected final MecComponentAnalyser mecAnalyser = new MecComponentAnalyser();

  protected final double precision;

  public OnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, RewardGenerator<S> rewardGenerator, 
                               int revisitThreshold, double rMax, double precision) {
    this.explorer = explorer;

    this.values = values;
    this.rewardGenerator = rewardGenerator;

    this.revisitThreshold = revisitThreshold;
    this.boundedMecQuotient = new BoundedMecQuotient<>(explorer.model());
    this.rMax = rMax;

    this.precision = precision;

  }

  /**
   * Returns explorer object consisting of the partial model.
   */
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

  /**
   * Returns lower and upper bounds of the reachability value of state.
   * @param state: Integer value of state for which value is to be found.
   */
  @Override
  public Bounds bounds(int state) {
    return values.bounds(this.boundedMecQuotient.representative(state));
  }

  /**
   * Initialize Sink State bounds.
   */
  protected void initSinkStates(){

    int plusState = BoundedMecQuotient.getPlusState();
    int minusState = BoundedMecQuotient.getMinusState();
    int uncertainState = BoundedMecQuotient.getUncertainState();

    assert values.bounds(plusState).lowerBound()==1;

    values.update(minusState, List.of());
    assert values.bounds(minusState).upperBound()==0;

    assert values.bounds(uncertainState).equals(Bounds.reachUnknown());

  }

  /**
   * Run OnDemandVI Algorithm.
   */
  public void run() throws PrismException {

    initSinkStates();

    logger.log(Level.INFO, "Initialized Sink States.");

    int run = 0;

    int initialState = explorer.initialStates().stream().findFirst().orElse(-1);
    assert initialState!=-1: "Explorer has no initial state";
    int representative = boundedMecQuotient.representative(initialState);

    // isSolved() defined in UnboundedReachValues
    while(!values.isSolved(representative)) {  // The values between upper and lower bounds for the initial states should,be less than epsilon
//      logger.log(Level.INFO, "Run "+run);
      if (sample(representative, run)) {
        // initialState may be part of an MEC and the MEC may be collapsed, and we may have a representative that is different
        // from initialState
        representative = boundedMecQuotient.representative(initialState);
      }
      run++;  // count of episodic runs
      if (run%1000==0){
//        logger.log(Level.INFO, "Bounds "+bounds(representative));
      }
    }

  }

  /**
   * Simulate a single iteration of the OnDemandVI algorithm. Encapsulates sample, findMec and update functions
   * of the algorithm.
   * @param initialState: Integer value of state from which sampling should start.
   * @param run: Integer value representing how many iterations of the algorithm have been done.
   * @return whether MECs were updated during the simulation.
   */
  protected boolean sample(int initialState, int run) throws PrismException {

    IntStack visitStack = new IntArrayList();
    int currentState = initialState;
    Int2IntOpenHashMap stateVisitCounts = new Int2IntOpenHashMap();  // keeps counts of the number of times a state is visited

    boolean updatedEC = false;
    boolean foundDesignatedSinkState = false;

    while(true){

      visitStack.push(currentState);
      stateVisitCounts.putIfAbsent(currentState, 0);
      stateVisitCounts.addTo(currentState, 1);

      // checks plus state,minus state and uncertain state
      if(BoundedMecQuotient.isSinkState(currentState)){
        foundDesignatedSinkState = true;
        break;
      }
      if(stateVisitCounts.get(currentState)>=this.revisitThreshold){
        updatedEC = true;
        break;
      }

      if(!explorer.isExploredState(currentState)) {
        explore(currentState);  // action choices etc. are populated in the partial model. The bounds of currentState are also initialised.
      }

      int nextState = values.sampleNextState(currentState, choices(currentState));

      // This is true when the currentState doesn't have any choices from it, i.e. it is a sink state.
      if (nextState == -1) {
        break;
      }

      currentState = nextState;

    }

    if(updatedEC){
      handleComponents();
    }
    else{
      // The last state can also be some normal sink state in the model
      if(foundDesignatedSinkState) {
        int sinkState = visitStack.popInt();
        if (BoundedMecQuotient.isUncertainState(sinkState) || BoundedMecQuotient.isPlusState(sinkState)) {
          int mecRepresentative = visitStack.popInt();
          updateMec(mecRepresentative);
        }
      }
    }

    // update the values of the states along the path that is stored in visitStack (Line 18--21 of OnDemandVI algorithm from CAV'17 paper)
    while(!visitStack.isEmpty()){
      // In a path there can be at most one sink state at the end. If such a sink state appears in the path, then it would have already
      // been popped inside the else block above.
      int state = boundedMecQuotient.representative(visitStack.popInt());
      assert !BoundedMecQuotient.isSinkState(state);
      values.update(state, choices(state));
    }

    return updatedEC;

  }

  protected Bounds getMecBounds(int mecRepresentative){
    return BoundedMecQuotient.getBoundsFromStayAction(boundedMecQuotient.getStayAction(mecRepresentative));
  }

  protected Mec getMec(int mecRepresentative){
    NatBitSet mecStates = NatBitSets.copyOf(explorer.exploredStates().stream()
            .filter(s -> boundedMecQuotient.representative(s)==mecRepresentative)
            .collect(Collectors.toList()));
    return Mec.create(explorer().model(), mecStates);
  }

  protected void updateStayAction(int mecRepresentative, Bounds scaledBounds){
    boundedMecQuotient.updateStayAction(mecRepresentative, scaledBounds);
  }

  /**
   * Implements lines 11-15 in CAV'17 paper. Runs VI on mec.
   * @param mecRepresentative: Representative state of mec on which VI has to be run.
   */
  protected void updateMec(int mecRepresentative){
    assert this instanceof BlackOnDemandValueIterator || boundedMecQuotient.representative(mecRepresentative) == mecRepresentative;

    // mecBounds now contain the scaled reward upper and lower bounds.
    Bounds mecBounds = getMecBounds(mecRepresentative);

    double currPrecision = mecBounds.difference()*this.rMax;

    if(currPrecision<this.precision){
      return;
    }

    double targetPrecision = currPrecision/2;

    logger.log(Level.INFO, "updating MEC");

    // get all the MEC states corresponding to mecRepresentative.
    Mec mec = getMec(mecRepresentative);

    if (mec.states.size()==0){
      return;
    }

    // It can be the case that the bounds are already very precise and running VI again can take a lot of time. We
    // essentially run VI from the start assuming that no progress has been made yet. There are 2 cases when this
    // happens. 1. There are new states in the MEC and VI needs to be run again. 2. The sampler reaches the uncertain
    // state (The probability of this happening is infinitesimally small).
    // In both these cases, it is fine to assume that no progress has been made. 1. Since, a new state has been added,
    // the reward can increase. 2. Since, the bounds are already very precise, very little would be achieved by making
    // VI more precise.
    // Note that in the second case, even though we don't need to run the VI at all, the cost of differentiating from
    // case 1 is more than the cost of simply running the VI for a few steps, so we run it anyway.
//    if(isZero(targetPrecision)){
//      targetPrecision = 0.5*this.rMax;
//    }

    assert !isZero(targetPrecision);

    // Fetch the precomputed value map of the mec from the cache, and if there isn't any, then returns an empty map.
    // The key of the map is mecRepresentative.
    Int2DoubleMap valueCache = mecValueCache.computeIfAbsent(mecRepresentative, s -> new Int2DoubleOpenHashMap());

    // lambda function that returns a state object when given the state index. required for accessing reward generator function.
    Int2ObjectFunction<S> stateIndexMap = explorer::getState;

    RestrictedMecValueIterator<S, M> valueIterator = new RestrictedMecValueIterator<>(this.explorer.model(), mec, targetPrecision, rewardGenerator, stateIndexMap, valueCache);

    valueIterator.run();

    Bounds newBounds = valueIterator.getBounds();
    Bounds scaledBounds = Bounds.of(newBounds.lowerBound()/this.rMax, newBounds.upperBound()/this.rMax);
    scaledBounds = scaledBounds.withLower(Math.max(scaledBounds.lowerBound(), mecBounds.lowerBound()));

    // In the case when we run VI from scratch, the new bounds may be worse than the previously computed bounds. In that
    // case we discard the new bounds. Specifically, we check if the new lower bound is worse than the previously
    // computer lower bound. In both cases the cases where we run VI from scratch, this operation is valid. 1. If the
    // new lower bound is lower than the previous lower bound, we can discard it safely as we can simply assume that
    // the optimal strategy wouldn't include newly added states and the VI didn't achieve anything. 2. We already had a
    // precise value, so we didn't need to run VI anyway.
    updateStayAction(mecRepresentative, scaledBounds);

    valueCache = valueIterator.getValues();
    mecValueCache.put(mecRepresentative, valueCache);
  }


  /**
   * Implements OnTheFlyEC from CAV'17 paper.
   */
  public boolean handleComponents(){

    if(!newStatesSinceCollapse){
      return false;
    }

    newStatesSinceCollapse = false;
    logger.log(Level.INFO, "Searching components");

    NatBitSet states = NatBitSets.copyOf(explorer.exploredStates());
    states.removeAll(boundedMecQuotient.removedStates()); // states variable now stores only the states in the current collapsed partial model.

    List<NatBitSet> newComponents = mecAnalyser.findComponents(boundedMecQuotient, states);  // find all MECs in the partial model.
    // This contains only newly found components. Since all previously found components are collapsed, they won't be recognized as MECs anymore.

    if(newComponents.isEmpty()){
      return false;
    }

    if (logger.isLoggable(Level.FINE)) {
      int count = newComponents.stream().mapToInt(NatBitSet::size).sum();
      logger.log(Level.FINE, "Found {0} new components with {1} new states",
              new Object[] {newComponents.size(), count});
    }

    // This collapses the sets of states into representatives. Further, the stay action is added here.
    IntList representatives = boundedMecQuotient.collapse(newComponents);
    var collapseIterator = newComponents.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while(collapseIterator.hasNext()){
      // the components and the corresponding representatives are in the same order.
      int representative = representativeIterator.nextInt();

      // Removing from cache as new states have been added to mec and all values need to computed again from start.
      mecValueCache.remove(representative);

      // Reset stay action bounds as mec has been expanded
      boundedMecQuotient.updateStayAction(representative, Bounds.of(BoundedMecQuotient.getBoundsFromStayAction(boundedMecQuotient.getStayAction(representative)).lowerBound(), 1));

      // We need to run VI on the MEC again to account for the following case. It can be that the bounds on the MEC are
      // already very precise. Thus, the probability of reaching the uncertain state would be very small and we may
      // never be able to run VI on the newly added states again. Thus, we need to run VI straight after adding new
      // states.
      updateMec(representative);

      // updates the bounds of the representative according to all actions of mec members going out of the MEC.
      values.collapse(representative, choices(representative), collapseIterator.next());
    }

    return true;

  }

  /**
   * Add state to partial model.
   * @param state: Integer value of state to be explored.
   */
  protected void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    assert !BoundedMecQuotient.isSinkState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);  //  state added to partial model, and explorer.isExploredState(state) is set to true.
  }

  /**
   * Fetch choices from state in partial model
   * @param state: Integer value of state from whom choices are required.
   * @return List of distributions of the choices from the state in the partial model.
   */
  protected List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    assert !BoundedMecQuotient.isSinkState(state);
    return boundedMecQuotient.getChoices(state);
  }

}
