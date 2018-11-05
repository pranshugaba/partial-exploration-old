package de.tum.in.prism.core.util;

import de.tum.in.naturals.set.NatBitSets;
import explicit.Distribution;
import explicit.MDPExplicit;
import explicit.NondetModelSimple;
import explicit.SuccessorsIterator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import prism.PrismException;

public class MDPSparse extends MDPExplicit implements NondetModelSimple {
  private final Map<Integer, List<Action>> transitions = new HashMap<>();
  private int numTransitions = 0;

  public MDPSparse() {
    initialise(0);
  }

  @Override
  public void clearState(int s) {
    transitions.remove(s);
  }

  @Override
  public int addState() {
    numStates += 1;
    return numStates - 1;
  }

  @Override
  public void addStates(int numToAdd) {
    numStates += numToAdd;
  }

  @Override
  public void buildFromPrismExplicit(String filename) throws PrismException {
    throw new PrismException("Unsupported Operation");
  }

  public int addChoice(int s, Distribution distr) {
    assert s < numStates;
    List<Action> distributions = transitions.computeIfAbsent(s, ArrayList::new);
    distributions.add(new Action(distr));
    numTransitions += 1;
    return distributions.size() - 1;
  }

  public int addActionLabelledChoice(int s, Distribution distr, Object action) {
    assert s < numStates;
    List<Action> distributions = transitions.computeIfAbsent(s, ArrayList::new);
    distributions.add(new Action(action, distr));
    numTransitions += 1;
    return distributions.size() - 1;
  }

  public void setTransitions(int s, List<Distribution> distribution) {
    assert s < numStates;
    transitions.put(s, distribution.stream().map(Action::new).collect(Collectors.toList()));
  }

  @Override
  public int getNumTransitions() {
    return numTransitions;
  }

  @Override
  public void findDeadlocks(boolean fix) {
    for (int state = 0; state < numStates; state++) {
      if (!transitions.containsKey(state)) {
        addDeadlockState(state);
        if (fix) {
          Distribution distr = new Distribution();
          distr.add(state, 1.0);
          addChoice(state, distr);
        }
      }
    }
  }

  @Override
  public void checkForDeadlocks(BitSet except) throws PrismException {
    IntStream statesToCheck = except == null
        ? IntStream.range(0, numStates)
        : NatBitSets.asBounded(NatBitSets.asSet(except), numStates).complement().intStream();

    OptionalInt deadlock = statesToCheck.filter(i -> !transitions.containsKey(i)).findAny();
    if (deadlock.isPresent()) {
      throw new PrismException("MDP has a deadlock in state " + deadlock.getAsInt());
    }
  }

  @Override
  public int getNumChoices(int s) {
    assert s < numStates;
    return transitions.getOrDefault(s, List.of()).size();
  }

  @Override
  public int getMaxNumChoices() {
    return transitions.values().stream().mapToInt(List::size).max().orElse(-1);
  }

  @Override
  public int getNumChoices() {
    return transitions.values().stream().mapToInt(List::size).sum();
  }

  @Override
  public Object getAction(int s, int i) {
    return transitions.getOrDefault(s, List.of()).get(i).label;
  }

  @Override
  public boolean allSuccessorsInSet(int s, int i, BitSet set) {
    return transitions.get(s).get(i).distribution.isSubsetOf(set);
  }

  @Override
  public boolean someSuccessorsInSet(int s, int i, BitSet set) {
    return transitions.get(s).get(i).distribution.containsOneOf(set);
  }

  @Override
  public Iterator<Integer> getSuccessorsIterator(int s, int i) {
    return transitions.get(s).get(i).distribution.getSupport().iterator();
  }

  @Override
  public SuccessorsIterator getSuccessors(int s, int i) {
    return SuccessorsIterator.from(getSuccessorsIterator(s, i), true);
  }

  @Override
  public int getNumTransitions(int s, int i) {
    return transitions.get(s).get(i).distribution.size();
  }

  // Accessors (for MDP)

  @Override
  public Iterator<Map.Entry<Integer, Double>> getTransitionsIterator(int s, int i) {
    return transitions.get(s).get(i).distribution.iterator();
  }

  private static final class Action {
    @Nullable
    public final Object label;
    public final Distribution distribution;

    public Action(Object label, Distribution distribution) {
      this.label = label;
      this.distribution = distribution;
    }

    public Action(Distribution distribution) {
      label = null;
      this.distribution = distribution;
    }
  }

}
