package de.tum.in.prism.util;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface Model extends explicit.ModelSimple {
  void setInitialStates(List<Integer> initialStates);


  List<Distribution> getChoices(int state);

  default void forEachChoice(int state, Consumer<Distribution> action) {
    getChoices(state).forEach(action);
  }

  default void forEachTransition(int state, int action, TransitionConsumer consumer) {
    getChoice(state, action).forEach(consumer);
  }

  void addChoice(int state, Distribution distribution);

  void addChoice(int state, Action action);

  void setChoice(int state, int action, Distribution distribution);

  Distribution getChoice(int state, int action);

  void setActions(int state, List<Action> actions);

  List<Action> getActions(int state);

  int getNumChoices(int state);

  @FunctionalInterface
  interface TransitionConsumer extends BiConsumer<Integer, Double> {
    void accept(int destination, double probability);

    @Override
    default void accept(Integer destination, Double probability) {
      accept(destination.intValue(), probability.doubleValue());
    }
  }
}
