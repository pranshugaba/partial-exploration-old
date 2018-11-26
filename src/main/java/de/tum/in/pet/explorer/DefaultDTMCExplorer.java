package de.tum.in.pet.explorer;

import de.tum.in.pet.model.Action;
import de.tum.in.pet.model.DTMC;
import de.tum.in.pet.model.Distribution;
import java.util.Collections;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public class DefaultDTMCExplorer extends AbstractExplorer<DTMC> {
  public DefaultDTMCExplorer(ModelGenerator generator) throws PrismException {
    super(generator, new DTMC());

    initialize();
  }

  @Override
  protected List<Action> getActionsOfExploredState(int stateNumber) throws PrismException {
    ModelGenerator generator = generator();
    int transitionCount = generator.getNumTransitions();
    Distribution distribution = new Distribution();

    for (int transition = 0; transition < transitionCount; transition++) {
      double transitionProbability = generator.getTransitionProbability(0, transition);
      State transitionTarget = generator.computeTransitionTarget(0, transition);
      int transitionTargetNumber = getStateNumber(transitionTarget);

      assert transitionProbability > 0.0d;
      distribution.add(transitionTargetNumber, transitionProbability);
    }
    return Collections.singletonList(Action.of(distribution));
  }
}
