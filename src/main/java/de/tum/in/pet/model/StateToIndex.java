package de.tum.in.pet.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import parser.State;

public class StateToIndex {
  private final Object2IntMap<State> stateMap = new Object2IntOpenHashMap<>();
  private final Int2ObjectMap<State> indexMap = new Int2ObjectOpenHashMap<>();

  public StateToIndex() {
    stateMap.defaultReturnValue(-1);
  }

  public void addState(State state, int number) {
    assert !indexMap.containsKey(number);
    stateMap.put(state, number);
    indexMap.put(number, state);
  }

  public int getStateNumber(State state) {
    return stateMap.getInt(state);
  }

  public boolean check(int stateNumber) {
    return indexMap.containsKey(stateNumber)
        && stateMap.getInt(indexMap.get(stateNumber)) == stateNumber;
  }

  public boolean contains(State state) {
    return stateMap.containsKey(state);
  }

  public State getState(int stateNumber) {
    return indexMap.get(stateNumber);
  }

  public int size() {
    return indexMap.size();
  }
}
