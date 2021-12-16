package de.tum.in.pet.SimulateMecPerformanceTest;

import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Random;

public class HeuristicMecSimulator {
    private final MarkovDecisionProcess mdp;
    private final double nSamples;
    private final Int2ObjectMap<ObjectArrayList<Int2LongMap>> stateTransitionCounts = new Int2ObjectOpenHashMap<>();

    public HeuristicMecSimulator(MarkovDecisionProcess mdp, double nSamples) {
        this.mdp = mdp;
        this.nSamples = nSamples;
        initializeCount();
    }

    public void simulate() {
        int currentState = 0;
        Random random = new Random();

        int simulationCount = 0;
        int nTransitions = getNumTransitions();
        double requiredSamples = nSamples * nTransitions;

        while (simulationCount < requiredSamples) {
            List<Action> intActions = mdp.getActions(currentState);
            int actionIndex = random.nextInt(intActions.size());
            int successor = mdp.getChoice(currentState, actionIndex).sample();
            incrementTransitionCount(currentState, actionIndex, successor);
            currentState = successor;
            simulationCount++;
        }
    }

    private int getNumTransitions() {
        int nTransitions = 0;
        for (int state = 0; state < mdp.getNumStates(); state++) {
            for (Action action : mdp.getActions(state)) {
                nTransitions = nTransitions + action.distribution().size();
            }
        }

        return nTransitions;
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
