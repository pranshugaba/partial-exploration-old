package de.tum.in.prism.core.explorer;

import de.tum.in.prism.util.Action;
import de.tum.in.prism.util.DTMC;
import de.tum.in.prism.util.Distribution;
import java.util.Collections;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public class EmbeddingCTMCExplorer extends AbstractExplorer<DTMC> {
  public EmbeddingCTMCExplorer(ModelGenerator generator) throws PrismException {
    super(generator, new DTMC());
  }

  @Override
  protected List<Action> getActionsOfExploredState(int stateNumber) throws PrismException {
    ModelGenerator generator = generator();
    int choiceCount = generator.getNumChoices();

    Distribution distribution = new Distribution();
    for (int choice = 0; choice < choiceCount; choice++) {
      int transitionCount = generator.getNumTransitions(choice);
      for (int transition = 0; transition < transitionCount; transition++) {
        double transitionProbability = generator.getTransitionProbability(choice, transition);
        State transitionTarget = generator.computeTransitionTarget(choice, transition);
        int transitionTargetNumber = addState(transitionTarget);

        assert transitionProbability > 0.0d;
        distribution.add(transitionTargetNumber, transitionProbability);
      }
    }

    return Collections.singletonList(Action.of(distribution.isEmpty()
        ? new Distribution(stateNumber, 1.0d) : distribution.scale()));
  }
}
