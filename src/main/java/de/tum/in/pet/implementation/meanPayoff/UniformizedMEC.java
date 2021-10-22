package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.util.StateActionSuccessorMap;
import it.unimi.dsi.fastutil.ints.*;

// This class holds info about mec states, actions and the uniformized rates of every transition.
public class UniformizedMEC {
    private final NatBitSet states;
    private final Int2ObjectMap<IntSet> actions;

    private final StateActionSuccessorMap<Double> uniformizedRates = new StateActionSuccessorMap<>();

    UniformizedMEC(NatBitSet states, Int2ObjectMap<IntSet> actions) {
        this.states = states;
        this.actions = actions;
    }

    public NatBitSet getStates() {
        return states;
    }

    public Int2ObjectMap<IntSet> getActions() {
        return actions;
    }

    public void setRate(int state, int action, int successor, double uniformizedRate) {
        assert states.contains(successor);
        assert actions.get(state).contains(action);
        assert states.contains(successor);

        uniformizedRates.set(state, action, successor, uniformizedRate);
    }

    public double getUniformizedRate(int state, int action, int successor) {
        return uniformizedRates.get(state, action, successor);
    }
}
