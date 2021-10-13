package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.Int2BooleanFunction;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

import java.util.List;
import java.util.function.IntPredicate;

public class GreyUnboundedReachValues extends BlackUnboundedReachValues {

    private Int2ObjectFunction<Int2IntFunction> getNumSuccessors = null;
    private Int2ObjectFunction<List<Distribution>> choiceFunction = null;
    private Int2BooleanFunction isInMEC = null;

    public GreyUnboundedReachValues(ValueUpdate update, UpdateMethod updateMethod, IntPredicate target, double precision,
                                     SuccessorHeuristic heuristic) {
        super(update, updateMethod, target, precision, heuristic);
    }

    public void setGetNumSuccessors(Int2ObjectFunction<Int2IntFunction> getNumSuccessors) {
        this.getNumSuccessors = getNumSuccessors;
    }

    public void setChoiceFunction(Int2ObjectFunction<List<Distribution>> choiceFunction) {
        this.choiceFunction = choiceFunction;
    }

    public void setIsInMEC(Int2BooleanFunction isInMEC) {
        this.isInMEC = isInMEC;
    }

    /**
     * If all successors are visited, then we don't have to use most conservative bounds. So we return false.
     * If all successors are not visited, then we return true to use most conservative bounds.
     */
    @Override
    protected boolean doMostConservativeGuess(int state, Distribution distribution) {
        int actionIndex = getActionIndex(state, distribution);

        //If it is a stay action, then we know all of it's successors, because we create them.
        if (isStayAction(state, actionIndex)) {
            return false;
        }

        assert actionIndex != -1;

        int actualSuccessors = getNumSuccessors.apply(state).apply(actionIndex);
        int numSuccessorsSeen = distribution.support().size();

        if (numSuccessorsSeen < actualSuccessors) {
            return true;
        }
        return super.doMostConservativeGuess(state, distribution);
    }

    private int getActionIndex(int state, Distribution distribution) {
        List<Distribution> stateActions = choiceFunction.apply(state);

        for (int i = 0; i < stateActions.size(); i++) {
            if (stateActions.get(i).equals(distribution)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isStayAction(int state, int actionIndex) {
        return isInMEC.apply(state) && (actionIndex == choiceFunction.apply(state).size()-1);
    }
}
