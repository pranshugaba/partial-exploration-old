package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.implementation.reachability.BlackUnboundedReachValues;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.BlackExplorer;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.doubles.Double2LongFunction;
import it.unimi.dsi.fastutil.ints.*;
import prism.PrismException;

import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static de.tum.in.probmodels.util.Util.isZero;

public class BlackOnDemandValueIterator<S, M extends Model> extends OnDemandValueIterator<S, M> {

  private final double pMin;
  private final double errorTolerance;
  private final Double2LongFunction nSampleFunction;

  private List<NatBitSet> mecs = new ArrayList<>();
  private Double mecConfidence = 1d;

  private final Int2IntMap stateToMecMap = new Int2IntOpenHashMap();
  private Int2ObjectMap<Distribution> stayActionMap = new Int2ObjectOpenHashMap<>();

  private Int2IntMap stayActionCounts = new Int2IntOpenHashMap();

  private boolean seenNewTransition = false;

  public BlackOnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, RewardGenerator<S> rewardGenerator,
                                    int revisitThreshold, double rMax, double pMin, double errorTolerance,
                                    Double2LongFunction nSampleFunction, double precision) {
    super(explorer, values, rewardGenerator, revisitThreshold, rMax, precision);
    this.pMin = pMin;
    this.errorTolerance = errorTolerance;
    this.nSampleFunction = nSampleFunction;
  }

  @Override
  public Bounds bounds(int state) {
    return values.bounds(state);
  }

  @Override
  protected boolean sample(int initialState, int run) throws PrismException {

    BlackUnboundedReachValues values = (BlackUnboundedReachValues) this.values;

    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) explorer();

    explorer.updateCountParams(mecConfidence, pMin);

    double k = Math.pow(2, run);
    long nIterations = nSampleFunction.apply(k);
    double errorTolerance = this.errorTolerance;

    Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction = x -> (y -> y < explorer.getChoices(x).size()
            ? Math.sqrt(-Math.log(mecConfidence)/(2*explorer.getActionCounts(x, y)))
            : 0);
    values.setConfidenceWidthFunction(confidenceWidthFunction);

    for (int i = 0; i < nIterations; i++) {

      IntList visitStack = new IntArrayList();
      int currentState = initialState;
      Int2IntOpenHashMap stateVisitCounts = new Int2IntOpenHashMap();  // keeps counts of the number of times a state is visited

      while (true) {

        visitStack.add(currentState);
        stateVisitCounts.putIfAbsent(currentState, 0);
        stateVisitCounts.addTo(currentState, 1);

        // checks plus state,minus state and uncertain state
        if (BoundedMecQuotient.isSinkState(currentState)) {
          visitStack.removeInt(visitStack.size() - 1);
          if (BoundedMecQuotient.isUncertainState(currentState)) {
            int mecIndex = stateToMecMap.get(visitStack.removeInt(visitStack.size() - 1));
            updateMec(mecIndex);
          }
          break;
        }

        if (!explorer().isExploredState(currentState)) {
          explore(currentState);  // action choices etc. are populated in the partial model. The bounds of currentState are also initialised.
        }

        List<Distribution> choices = choices(currentState);

        if (stateVisitCounts.get(currentState)>=revisitThreshold) {
          if (looping(visitStack)){
            break;
          }
        }

        int nextState;
        if (choices.isEmpty()){
          nextState = -1;
        }
        else {
          int nextActionIndex = values.sampleNextAction(currentState, choices);
          if (nextActionIndex == -1) {
            nextActionIndex = explorer.sampleNextAction(currentState);
          }

          assert nextActionIndex != -1;

          if (nextActionIndex == choices.size()-1 && stateToMecMap.containsKey(currentState)){
            nextState = choices.get(nextActionIndex).sample();
            stayActionCounts.put(stateToMecMap.get(currentState),
                    stayActionCounts.get(stateToMecMap.get(currentState))+1);
          }
          else {
            nextState = explorer.sampleState(currentState, nextActionIndex);
            seenNewTransition |= explorer.updateCounts(currentState, nextActionIndex, nextState, true);
          }
        }

        // This is true when the currentState doesn't have any choices from it, i.e. it is a sink state.
        if (nextState == -1) {
          break;
        }

        currentState = nextState;

        mecConfidence = errorTolerance*pMin/explorer.getNumTrans();
        explorer.updateCountParams(mecConfidence, pMin);

      }


    }

    handleComponents();

    values.resetBounds();
    initSinkStates();

//    Int2ObjectMap<List<Integer>> transformedChoices = new Int2ObjectOpenHashMap<>();
//    for (int state: explorer.exploredStates()){
//      NatBitSet mec = stateToMecMap.containsKey(state) ? mecs.get(stateToMecMap.get(state)) : NatBitSets.emptySet();
//      // Function to remap distributions
//      IntUnaryOperator map = successor -> mec.contains(successor) ? -1 : successor;
//
//      List<Integer> stateTransformedChoices = getTransformedChoices(state, map);
//      transformedChoices.put(state, stateTransformedChoices);
//    }

//    confidenceWidthFunction = x -> (y -> (transformedChoices.get(x).get(y) < explorer.getChoices(x).size()
//            ? Math.sqrt(-Math.log(mecConfidence)/(2*explorer.getActionCounts(x, transformedChoices.get(x).get(y))))
//            : 0));
    confidenceWidthFunction = x -> (y -> y < explorer.getChoices(x).size()
            ? Math.sqrt(-Math.log(mecConfidence)/(2*explorer.getActionCounts(x, y)))
            : 0);
    values.setConfidenceWidthFunction(confidenceWidthFunction);

    boolean ifProgress = true;
    while(ifProgress) {
      ifProgress = update();
    }

    return true;

  }

  private boolean update(){
    BlackUnboundedReachValues values = (BlackUnboundedReachValues) this.values;
    values.cacheCurrBounds();

    for (int state: explorer.exploredStates()){
      List<Distribution> realChoices = choices(state);
      values.update(state, realChoices);
    }

    for (NatBitSet mec : this.mecs) {
      values.deflate(mec, this::choices);
    }

    return values.checkProgress();
  }

  @Override
  protected Bounds getMecBounds(int mecIndex) {
    return BoundedMecQuotient.getBoundsFromStayAction(stayActionMap.get(mecIndex));
  }

  @Override
  protected Mec getMec(int mecIndex) {

    NatBitSet mecStates = NatBitSets.copyOf(stateToMecMap.keySet().stream()
            .filter(key -> stateToMecMap.get((int) key)==mecIndex).collect(Collectors.toSet()));

    return Mec.create(explorer().model(), mecStates);
  }

  @Override
  protected void updateStayAction(int mecIndex, Bounds scaledBounds) {
    Distribution stayAction = BoundedMecQuotient.getStayDistribution(scaledBounds);
    stayActionMap.put(mecIndex, stayAction);
  }

  @Override
  public boolean handleComponents(){

    if(!seenNewTransition){
      return false;
    }

    seenNewTransition = false;

    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) explorer();
    BlackUnboundedReachValues values = (BlackUnboundedReachValues) this.values;

    logger.log(Level.INFO, "Searching components");

    NatBitSet states = NatBitSets.copyOf(explorer.exploredStates());

    explorer.activateActionCountFilter();
    List<NatBitSet> newComponents = mecAnalyser.findComponents(explorer.model(), states);  // find all MECs in the partial model.

    if(newComponents.isEmpty()){
      boolean boolRet = this.mecs.size() > 0;
      this.mecs.clear();
      this.stateToMecMap.clear();
      this.stayActionMap.clear();
      explorer.deactivateActionCountFilter();
      return boolRet;
    }

    NatBitSet changedMecs = updateMecInfo(newComponents);

    if (changedMecs.size()>0){
      logger.log(Level.INFO, "Found {0} new components", changedMecs.size());
    }
    else {
      explorer.deactivateActionCountFilter();
      return false;
    }

    // This collapses the sets of states into representatives. Further, the stay action is added here.

    for(int i: changedMecs){
      NatBitSet newComponent = newComponents.get(i);

      // We need to run VI on the MEC again to account for the following case. It can be that the bounds on the MEC are
      // already very precise. Thus, the probability of reaching the uncertain state would be very small and we may
      // never be able to run VI on the newly added states again. Thus, we need to run VI straight after adding new
      // states.
      updateMec(i);

      values.deflate(newComponent, this::choices);
    }

    explorer.deactivateActionCountFilter();
    return true;

  }

  private NatBitSet updateMecInfo(List<NatBitSet> newComponents){

    newComponents.sort((t1, t2) -> {
      if(t1.firstInt()<t2.firstInt()){
        return -1;
      }
      else if(t1.firstInt()>t2.firstInt()){
        return 1;
      }
      return 0;
    });

    int i=0;

    IntList unchangedMecs = new IntArrayList();
    IntList newIndices = new IntArrayList();

    for(NatBitSet newComponent: newComponents){
      int mecIndex = stateToMecMap.getOrDefault(newComponent.firstInt(), -1);
      for(int state: newComponent){
        int stateMecIndex = stateToMecMap.getOrDefault(state, -1);
        if(stateMecIndex!=mecIndex){
          mecIndex = stateMecIndex;
        }
      }
      if (mecIndex != -1 && mecs.get(mecIndex).size() == newComponent.size()){
        unchangedMecs.add(mecIndex);
        newIndices.add(i);
      }
      i++;
    }

    Int2ObjectMap<Distribution> newStayActionMap = new Int2ObjectOpenHashMap<>();
    Int2IntMap newStayActionCounts = new Int2IntOpenHashMap();
    Int2ObjectMap<Int2DoubleMap> newMecValueCache = new Int2ObjectOpenHashMap<>();

    for(i=0; i<unchangedMecs.size(); i++){
      newStayActionMap.put(newIndices.getInt(i), stayActionMap.get(unchangedMecs.getInt(i)));
      newStayActionCounts.put(newIndices.getInt(i), stayActionCounts.get(unchangedMecs.getInt(i)));
      newMecValueCache.put(newIndices.getInt(i), mecValueCache.get(unchangedMecs.getInt(i)));
    }

    stayActionMap = newStayActionMap;
    stayActionCounts = newStayActionCounts;
    mecValueCache = newMecValueCache;
    this.mecs = newComponents;

    stateToMecMap.clear();

    for(i=0; i<this.mecs.size(); i++){
      for(int state: this.mecs.get(i)){
        stateToMecMap.put(state, i);
      }
    }


    NatBitSet newMecs = NatBitSets.ensureModifiable(NatBitSets.boundedFullSet(newComponents.size()));
    newMecs.andNot(newIndices);

    assert stayActionMap.size() + newMecs.size() == mecs.size();

    for(int mecIndex: newMecs){
      stayActionCounts.put(mecIndex, 0);
      stayActionMap.put(mecIndex, BoundedMecQuotient.getStayDistribution(Bounds.reachUnknown()));
    }

    return newMecs;

  }

  private List<Integer> getTransformedChoices(int state, IntUnaryOperator map){
    List<Distribution> choices = choices(state);
    List<Integer> transformedChoices = new ArrayList<>();

    for (int i=0; i<choices.size(); i++) {
      Distribution choice = choices.get(i);
      if (choice.support().size()>0 &&
              choice.support().stream().allMatch(successor -> map.applyAsInt(successor)==-1)){
        continue;
      }

      transformedChoices.add(i);
    }

    return transformedChoices;
  }

  private boolean looping(IntList visitStack){

    handleComponents();
    return stateToMecMap.containsKey(visitStack.getInt(visitStack.size()-1));
  }

  @Override
  protected List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    assert !BoundedMecQuotient.isSinkState(state);

    List<Distribution> choices = new ArrayList<>(explorer.getChoices(state));
    if (stateToMecMap.containsKey(state)) {
      choices.add(stayActionMap.get(stateToMecMap.get(state)));
    }
    return choices;
  }

}
