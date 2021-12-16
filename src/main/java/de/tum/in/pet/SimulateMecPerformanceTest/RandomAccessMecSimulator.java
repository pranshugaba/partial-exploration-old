package de.tum.in.pet.SimulateMecPerformanceTest;

import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class RandomAccessMecSimulator {
    private final MarkovDecisionProcess mdp;
    private final double nSamples;
    private final Int2ObjectMap<ObjectArrayList<Int2LongMap>> stateTransitionCounts = new Int2ObjectOpenHashMap<>();

    public RandomAccessMecSimulator(MarkovDecisionProcess mdp, double nSamples) {
        this.mdp = mdp;
        this.nSamples = nSamples;
        initializeCount();
    }

    public void simulate() {
        for (int state = 0; state < mdp.getNumStates(); state++) {
            for (int actionIndex = 0; actionIndex < mdp.getActions(state).size(); actionIndex++) {
                simulateActionRepeatedly(state, actionIndex);
            }
        }
    }

    private void simulateActionRepeatedly(int state, int actionIndex) {
        long visitCount = getActionCounts(state, actionIndex);
        while (visitCount < nSamples) {
            int successor = mdp.getChoice(state, actionIndex).sample();
            incrementTransitionCount(state, actionIndex, successor);
            visitCount++;
        }
    }

    private long getActionCounts(int stateId, int actionIndex) {
        Int2LongMap transitionCounts = stateTransitionCounts.get(stateId).get(actionIndex);
        return transitionCounts.values().stream().mapToLong(s -> s).sum();
    }

    private void initializeCount() {
        for (int state = 0; state < mdp.getNumStates(); state++) {
            ObjectArrayList<Int2LongMap> stateActionCounts = new ObjectArrayList<>();
            for (Action ignored : mdp.getActions(state)) {
                stateActionCounts.add(new Int2LongOpenHashMap());
            }
            stateTransitionCounts.put(state, stateActionCounts);
        }
    }

    private void incrementTransitionCount(int state, int actionIndex, int successor) {
        Int2LongMap transitionCounts = stateTransitionCounts.get(state).get(actionIndex);

        long newTransitionCount = transitionCounts.getOrDefault(successor, 0) + 1;
        transitionCounts.put(successor, newTransitionCount);
    }
}
