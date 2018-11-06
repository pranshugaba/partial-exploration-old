package de.tum.in.prism.core.explorer;

import de.tum.in.prism.util.Action;
import de.tum.in.prism.util.DTMC;
import de.tum.in.prism.util.Distribution;
import java.util.Collections;
import java.util.List;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;
import prism.PrismUtils;

public class UniformizingCTMCExplorer extends AbstractExplorer<DTMC> {
  private final double rate;

  public UniformizingCTMCExplorer(ModelGenerator generator, double rate) throws PrismException {
    super(generator, new DTMC());
    if (PrismUtils.doublesAreEqual(rate, 0.0d)) {
      throw new IllegalArgumentException();
    }
    this.rate = rate;
  }

  @Override
  protected List<Action> getActionsOfExploredState(int stateNumber) throws PrismException {
    ModelGenerator generator = generator();

    int choiceCount = generator.getNumChoices();
    Distribution distribution = new Distribution();
    double sum = 0.0d;
    for (int choice = 0; choice < choiceCount; choice++) {
      int transitionCount = generator.getNumTransitions(choice);
      for (int transition = 0; transition < transitionCount; transition++) {
        double transitionProbability = generator.getTransitionProbability(choice, transition);
        State transitionTarget = generator.computeTransitionTarget(choice, transition);
        int transitionTargetNumber = addState(transitionTarget);

        assert transitionProbability > 0.0d;
        if (transitionTargetNumber != stateNumber) {
          sum += transitionProbability;
        }
        distribution.add(transitionTargetNumber, transitionProbability / rate);
      }
    }

    if (sum < rate) {
      distribution.set(stateNumber, 1 - (sum / rate));
    }
    assert PrismUtils.doublesAreEqual(distribution.sum(), 1.0d) : distribution.sum();
    return Collections.singletonList(Action.of(distribution));
  }
}
