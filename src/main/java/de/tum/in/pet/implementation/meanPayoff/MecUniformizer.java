package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.probmodels.graph.Mec;
import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

public class MecUniformizer {

    // Given a state and an action, rateFunction should return the rate associated with the pair.
    private Int2ObjectFunction<Int2DoubleFunction> rateFunction;

    // Given a state, action, successor this function should return the transition probability.
    private Int2ObjectFunction<Int2ObjectFunction<Int2DoubleFunction>> transitionProbabilityFunction;

    // Given a state and an action, this function should return all of their successors
    private Int2ObjectFunction<Int2ObjectFunction<NatBitSet>> successorFunction;

    public void setRateFunction(Int2ObjectFunction<Int2DoubleFunction> rateFunction) {
        this.rateFunction = rateFunction;
    }

    public void setTransitionProbabilityFunction(Int2ObjectFunction<Int2ObjectFunction<Int2DoubleFunction>> transitionProbabilityFunction) {
        this.transitionProbabilityFunction = transitionProbabilityFunction;
    }

    public void setSuccessorFunction(Int2ObjectFunction<Int2ObjectFunction<NatBitSet>> successorFunction) {
        this.successorFunction = successorFunction;
    }

    public UniformizedMEC uniformize(Mec mec, double maxRate) {
        UniformizedMEC uniformizedMEC = new UniformizedMEC(mec.states, mec.actions);

        // For every state-action pair in MEC,
        // get the rate, set self loop, adjust the transition probabilities
        for (Integer state : mec.states) {
            for (Integer action : mec.actions.get(state.intValue())) {
                uniformizeStateAction(state, action, uniformizedMEC, maxRate);
            }
        }

        return uniformizedMEC;
    }

    private void uniformizeStateAction(int state, int action, UniformizedMEC uniformizedMEC, double maxRate) {
        double rate = rateFunction.apply(state).apply(action);
        double rateToMaxRate = rate/maxRate;

        NatBitSet successors = successorFunction.apply(state).apply(action);
        for (Integer successor : successors) {
            double transitionProbability = transitionProbabilityFunction.apply(state).apply(action).apply(successor);

            double uniformizedRate;

            if (successor == state) {
                uniformizedRate = (rateToMaxRate * transitionProbability) +  (1 - rateToMaxRate);
            }
            else {
                uniformizedRate = rateToMaxRate * transitionProbability;
            }

            uniformizedMEC.setRate(state, action, successor, uniformizedRate);
        }


        if (!successors.contains(state)) {
            uniformizedMEC.setRate(state, action, state, 1 - rateToMaxRate);
        }
    }
}
