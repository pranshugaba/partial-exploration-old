package de.tum.in.pet.SimulateMecPerformanceTest;

import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import prism.Pair;

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

    public boolean simulate(double timeout) {
        double timeup = System.currentTimeMillis() + timeout;
        int currentState = 0;
        Random random = new Random();

        int simulationCount = 0;
        int nTransitions = getNumTransitions();

        // nSamples is the number of times a state-action pair needs to be visited.
        // requiredSamples is the number of simulations we perform during heuristic mec simulation.
        double requiredSamples = nSamples*nTransitions;

        if (requiredSamples < 0) {
            throw new IllegalArgumentException("Overflow occurred in requiredSamples");
        }

        Pair<Integer, Integer> leastStateAction = getLeastVisitedStateAction(mdp);
        int leastVisitedState = leastStateAction.first;
        int leastVisitedAction = leastStateAction.second;
        boolean runSimulation = getActionCounts(leastVisitedState, leastVisitedAction) < nSamples;

        while (runSimulation && (System.currentTimeMillis() < timeup)) {
            List<Action> intActions = mdp.getActions(currentState);
            int actionIndex = random.nextInt(intActions.size());
            int successor = mdp.getChoice(currentState, actionIndex).sample();
            incrementTransitionCount(currentState, actionIndex, successor);
            currentState = successor;
            simulationCount++;

            long leastStateActionCounts = getActionCounts(leastVisitedState, leastVisitedAction);

            if (leastStateActionCounts >= nSamples) {
                leastStateAction = getLeastVisitedStateAction(mdp);
                leastVisitedState = leastStateAction.first;
                leastVisitedAction = leastStateAction.second;
                leastStateActionCounts = getActionCounts(leastVisitedState, leastVisitedAction);
            }

            runSimulation = leastStateActionCounts < nSamples && (simulationCount < requiredSamples);
        }

        return System.currentTimeMillis() >= timeup;
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

    private Pair<Integer, Integer> getLeastVisitedStateAction(MarkovDecisionProcess mdp) {
        long minValue = Long.MAX_VALUE;
        int minVisitedState = -1;
        int minVisitedAction = -1;

        for (int state = 0; state < mdp.getNumStates(); state++) {
            for (int actionIndex = 0; actionIndex < mdp.getActions(state).size(); actionIndex++) {
                long counts = getActionCounts(state, actionIndex);
                if (counts < minValue) {
                    minVisitedState = state;
                    minVisitedAction = actionIndex;
                }
            }
        }

        return new Pair<>(minVisitedState, minVisitedAction);
    }

    private long getActionCounts(int stateId, int actionIndex) {
        Int2LongMap transitionCounts = stateTransitionCounts.get(stateId).get(actionIndex);
        return transitionCounts.values().stream().mapToLong(s -> s).sum();
    }
}
