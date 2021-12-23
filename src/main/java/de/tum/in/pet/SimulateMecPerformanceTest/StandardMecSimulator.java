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

public class StandardMecSimulator {
    private final MarkovDecisionProcess mdp;
    private final double nSamples;
    private final Int2ObjectMap<ObjectArrayList<Int2LongMap>> stateTransitionCounts = new Int2ObjectOpenHashMap<>();

    public StandardMecSimulator(MarkovDecisionProcess mdp, double nSamples) {
        this.mdp = mdp;
        this.nSamples = nSamples;
        initializeCount();
    }

    // Returns True if it stopped due to timeout
    public boolean simulate(double timeout) {
        double timeUp = System.currentTimeMillis() + timeout;
        int currentState = 0;
        Random random = new Random();

        Pair<Integer, Integer> leastStateAction = getLeastVisitedStateAction(mdp);
        int leastVisitedState = leastStateAction.first;
        int leastVisitedAction = leastStateAction.second;

        // We terminate the simulation if lest visited state action pair in Mec is at least visited requiredSamples number of times
        boolean runSimulation = getActionCounts(leastVisitedState, leastVisitedAction) < nSamples;

        while (runSimulation && (System.currentTimeMillis() < timeUp)) {
            List<Action> intActions = mdp.getActions(currentState);
            int actionIndex = random.nextInt(intActions.size());
            int successor = mdp.getChoice(currentState, actionIndex).sample();
            incrementTransitionCount(currentState, actionIndex, successor);
            currentState = successor;

            long leastStateActionCounts = getActionCounts(leastVisitedState, leastVisitedAction);

            if (leastStateActionCounts >= nSamples) {
                leastStateAction = getLeastVisitedStateAction(mdp);
                leastVisitedState = leastStateAction.first;
                leastVisitedAction = leastStateAction.second;
                leastStateActionCounts = getActionCounts(leastVisitedState, leastVisitedAction);
            }

            runSimulation = leastStateActionCounts < nSamples;
        }

        return System.currentTimeMillis() >= timeUp;
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

    private long getActionCounts(int stateId, int actionIndex) {
        Int2LongMap transitionCounts = stateTransitionCounts.get(stateId).get(actionIndex);
        return transitionCounts.values().stream().mapToLong(s -> s).sum();
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

    private void incrementTransitionCount(int state, int actionIndex, int successor) {
        Int2LongMap transitionCounts = stateTransitionCounts.get(state).get(actionIndex);

        long newTransitionCount = transitionCounts.getOrDefault(successor, 0) + 1;
        transitionCounts.put(successor, newTransitionCount);
    }
}
