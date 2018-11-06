package de.tum.in.prism.core.explorer;

import de.tum.in.prism.util.Action;
import de.tum.in.prism.util.Distribution;
import de.tum.in.prism.util.MDP;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public class DefaultMDPExplorer extends AbstractExplorer<MDP> {
  public DefaultMDPExplorer(ModelGenerator generator) throws PrismException {
    super(generator, new MDP());
  }

  @Override
  protected List<Action> getActionsOfExploredState(int stateNumber) throws PrismException {
    ModelGenerator generator = generator();

    int actionCount = generator.getNumChoices();
    if (actionCount == 0) {
      return Collections.emptyList();
    }
    List<Action> actions = new ArrayList<>(actionCount);

    for (int action = 0; action < actionCount; action++) {
      int transitionCount = generator.getNumTransitions(action);

      Distribution distribution = new Distribution();
      for (int transition = 0; transition < transitionCount; transition++) {
        double transitionProbability = generator.getTransitionProbability(action, transition);
        State transitionTarget = generator.computeTransitionTarget(action, transition);
        int transitionTargetNumber = addState(transitionTarget);
        distribution.add(transitionTargetNumber, transitionProbability);
      }

      Object actionLabel = generator.getChoiceAction(action);
      actions.add(Action.of(distribution, actionLabel));
    }

    return actions;
  }
}
