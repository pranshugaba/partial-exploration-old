package de.tum.in.prism.core.explorer;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.unionfind.IntArrayUnionFind;
import de.tum.in.naturals.unionfind.IntUnionFind;
import de.tum.in.prism.core.util.Util;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;

public class StateCollapse {
  private final IntUnionFind collapsedStatesRepresentatives = new IntArrayUnionFind(0);
  private final NatBitSet exploredStates = NatBitSets.set();
  private final NatBitSet states = NatBitSets.set();
  private final NatBitSet collapsedStates = NatBitSets.set();

  public void addState(int state) {
    states.set(state);
    if (collapsedStatesRepresentatives.size() <= state) {
      collapsedStatesRepresentatives.add(collapsedStatesRepresentatives.size() / 2 + 1);
    }
  }

  public void setExplored(int state) {
    assert states.contains(state);
    exploredStates.set(state);
  }

  public int collapse(IntSet states) {
    assert !states.isEmpty();
    if (states.size() == 1) {
      int state = states.iterator().nextInt();
      collapsedStates.set(state);
      return state;
    }

    int anyState = collapsedStatesRepresentatives.find(states.iterator().nextInt());
    states.forEach((int state) -> collapsedStatesRepresentatives.union(anyState, state));

    int representativeState = collapsedStatesRepresentatives.find(anyState);

    exploredStates.andNot(states);
    this.states.andNot(states);
    exploredStates.set(representativeState);
    this.states.set(representativeState);

    collapsedStates.or(states);
    return representativeState;
  }

  public boolean isExplored(int number) {
    assert states.contains(number) : "State " + number + " not added";
    return exploredStates.contains(number);
  }

  public boolean isCollapsed(int state) {
    assert (collapsedStatesRepresentatives.find(state) == state) == states.contains(state);
    return !states.contains(state);
  }

  public int exploredCount() {
    return exploredStates.size();
  }

  public int fringeCount() {
    return states.size() - exploredStates.size();
  }

  public int getRepresentative(int state) {
    return collapsedStatesRepresentatives.find(state);
  }

  public List<NatBitSet> getCollapsedPartition() {
    return Util.unionFindPartition(collapsedStates.iterator(), collapsedStatesRepresentatives);
  }

  public NatBitSet getStates() {
    return states;
  }

  public NatBitSet getExploredStates() {
    return exploredStates;
  }
}
