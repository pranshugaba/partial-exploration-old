package de.tum.in.pet.mecGenerator;

import de.tum.in.probmodels.model.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class is used to generate MEC for testing different MEC simulation algorithms
 */
public class MdpMecGenerator {

    private MarkovDecisionProcess mdpMec;

    // We store states from 1 to n, explicitly to shuffle and pick random successors
    private List<Integer> statesList;
    private int numStates;

    // For each state, we can have up to 4 actions
    private static final int NUM_ACTIONS_BOUND = 4;

    // For each action, we can have up to 4 transitions
    private static final int NUM_TRANSITIONS_BOUND = 4;

    private final Random random = new Random();

    public MarkovDecisionProcess createMec(int numStates) {
        initialiseStateVariables(numStates);
        fillMdpWithRandomActions();
        mdpMec.addInitialState(0);
        return mdpMec;
    }

    private void fillMdpWithRandomActions() {
        for (int state = 0; state < numStates; state++) {
            List<Action> actions = getRandomActions(state);
            mdpMec.setActions(state, actions);
        }
    }

    private List<Action> getRandomActions(int state) {
        List<Action> generatedActions = new ArrayList<>();

        // We randomly choose the number of actions for each state
        int numActions = getRandomNumberInRange(1, NUM_ACTIONS_BOUND);

        for (int actionIndex = 0; actionIndex < numActions; actionIndex++) {
            Action action = getRandomAction(state);
            generatedActions.add(action);
        }

        return generatedActions;
    }

    // For each action, we randomly pick the number of transitions and the successors of those transitions.
    // Number of transitions will be randomly picked from [1 ... NUM_TRANSITIONS_BOUND]
    // For an action, all of its transitions will have equal probability (for convenience)
    private Action getRandomAction(int state) {
        // To pick n random successors, we shuffle the states and pick the first n elements
        Collections.shuffle(statesList);

        // We randomly pick the number of successors
        int numSuccessors = getRandomNumberInRange(1, NUM_TRANSITIONS_BOUND);
        List<Integer> successors = new ArrayList<>();

        // Random transition probabilities of those successors. This always adds up to 1.
        List<Double> transitionProbabilities = getRandomTransitionProbabilities(numSuccessors);

        // We force that at-least one successor is state+1. For the last state, we connect it with state 0.
        // This is to force a big MEC. If not forced, then this generator can possibly return an MDP which is not an MEC,
        // but it may have some connected components within it.
        if (state < numStates-1) {
            successors.add(state + 1);
        } else {
            successors.add(0);
        }

        for (int successor = 1; successor < numSuccessors; successor++) {
            successors.add(statesList.get(successor));
        }

        DistributionBuilder builder = Distributions.defaultBuilder();
        for (int i = 0; i < numSuccessors; i++) {
            builder.add(successors.get(i), transitionProbabilities.get(i));
        }

        return Action.of(builder.build());
    }

    private void initialiseStateVariables(int numStates) {
        this.numStates = numStates;
        mdpMec = new MarkovDecisionProcess();
        mdpMec.addStates(numStates);

        statesList = IntStream.range(0, numStates).boxed().collect(Collectors.toList());
    }

    private List<Double> getRandomTransitionProbabilities(int numTransitions) {
        if (numTransitions <= 0) {
            throw new IllegalArgumentException("numTransitions is less than 0");
        }

        //We choose numbers from 1..100, such that their sum is 100.
        //We then convert this to transition probabilities by dividing by 100.
        int remainingTransitions = numTransitions;
        int minValue = 1;
        int maxValue = 100 - (remainingTransitions - 1);
        int tempNumber;
        List<Integer> numbers = new ArrayList<>();
        int sumSoFar = 0;
        while (true) {
            if (remainingTransitions == 1) {
                numbers.add(100 - sumSoFar);
                break;
            }

            tempNumber = getRandomNumberInRange(minValue, maxValue);
            numbers.add(tempNumber);
            sumSoFar += tempNumber;
            remainingTransitions--;

            maxValue = 100 - sumSoFar - (remainingTransitions - 1);
            if (remainingTransitions == 1) {
                minValue = maxValue;
            }
        }

        return numbers.stream()
                .mapToDouble(num -> num/100.0)
                .boxed()
                .collect(Collectors.toList());
    }

    private int getRandomNumberInRange(int min, int max) {
        if (min == max) {
            return min;
        }

        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }

        return random.nextInt((max - min) + 1) + min;
    }
}
