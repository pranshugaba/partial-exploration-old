package de.tum.in.pet.util;

import de.tum.in.naturals.set.NatBitSet;
import explicit.MDP;
import explicit.rewards.MDPRewards;
import explicit.rewards.MDPRewardsSimple;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

public class RestrictedMdp {
  public final MDP mdp;
  private final IntUnaryOperator stateMapping;
  private final IntFunction<NatBitSet> stateActions;

  public RestrictedMdp(MDP mdp, IntUnaryOperator stateMapping,
      IntFunction<NatBitSet> stateActions) {
    this.mdp = mdp;
    this.stateMapping = stateMapping;
    this.stateActions = stateActions;
  }

  public MDPRewards buildRewards(MDPRewards originalRewards) {
    int states = mdp.getNumStates();

    MDPRewardsSimple rewards = new MDPRewardsSimple(states);
    for (int stateNumber = 0; stateNumber < states; stateNumber++) {
      int originalState = stateMapping.applyAsInt(stateNumber);
      double stateReward = originalRewards.getStateReward(originalState);
      rewards.setStateReward(stateNumber, stateReward);

      NatBitSet actions = stateActions.apply(stateNumber);
      int index = 0;
      IntIterator iterator = actions.iterator();
      while (iterator.hasNext()) {
        int originalIndex = iterator.nextInt();

        double transitionReward = originalRewards.getTransitionReward(stateNumber, originalIndex);
        rewards.setTransitionReward(stateNumber, index, transitionReward);
        index += 1;
      }
    }

    return rewards;
  }
}
