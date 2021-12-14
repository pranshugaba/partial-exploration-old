package de.tum.in.pet.SimulateMecPerformanceTest;

import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;

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

    private Map<Integer, Boolean> hasIncomingTransitionsFromOtherState;
    private Map<Integer, Boolean> hasOutgoingTransitionsToOtherState;

    // For each state, we randomly choose number of actions to be from [1...NUM_ACTIONS_BOUND]
    private static final int NUM_ACTIONS_BOUND = 3;

    // For each action, we randomly choose the number of transitions to be from [1...NUM_TRANSITION_BOUND]
    private static final int NUM_TRANSITIONS_BOUND = 3;

    private final Random random = new Random();

    public MarkovDecisionProcess createMec(int numStates) {
        initialiseStateVariables(numStates);
        fillMdpWithRandomActions();
        makeItAsAnMEC();

        return mdpMec;
    }

    // NOTE : Just filling up Mdp with random actions will not guarantee an MEC
    // After filling it up, we check for any states without incoming/outgoing transitions from/to other states
    // If present, we add an incoming/outgoing transition to that state, by creating an action with 1 transition in it
    private void fillMdpWithRandomActions() {
        for (int state = 0; state < numStates; state++) {
            List<Action> actions = getRandomActions(state);
            mdpMec.setActions(state, actions);
        }
    }

    // For each state we randomly pick the number of actions to be from [1 ... NUM_ACTIONS_BOUND]
    private List<Action> getRandomActions(int state) {
        List<Action> generatedActions = new ArrayList<>();

        int numActions = random.nextInt(NUM_ACTIONS_BOUND) + 1;
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
        int numSuccessors = random.nextInt(NUM_TRANSITIONS_BOUND) + 1;

        // To pick n random successors, we shuffle the states and pick the first n elements
        Collections.shuffle(statesList);

        // We give equal probabilities to all the transitions
        double prob = 1/ ((double) numSuccessors);
        DistributionBuilder builder = Distributions.defaultBuilder();
        for (int successor = 0; successor < numSuccessors; successor++) {
            if (successor != state) {
                hasIncomingTransitionsFromOtherState.put(successor, true);
                hasOutgoingTransitionsToOtherState.put(state, true);
            }
            builder.add(successor, prob);
        }

        Distribution distribution = builder.build();
        return Action.of(distribution);
    }

    // If every state has an incoming transition and an outgoing transition (Not from/to the same state itself),
    // then we can say it is an MEC
    private void makeItAsAnMEC() {
        for (int state = 0; state < numStates; state++) {
            boolean v = hasIncomingTransitionsFromOtherState.getOrDefault(state, false);
            if (!v) {
                addIncomingTransition(state);
            }

            v = hasOutgoingTransitionsToOtherState.getOrDefault(state, false);
            if (!v) {
                addOutgoingTransition(state);
            }
        }
    }

    // We randomly pick a source other than the given state, and add a single transition
    private void addIncomingTransition(int state) {
        int source = state;

        // We pick a target different from given state
        while (source == state) {
            source = random.nextInt(numStates);
        }

        DistributionBuilder builder = Distributions.defaultBuilder();
        builder.add(state, 1);
        mdpMec.addChoice(source, Action.of(builder.build()));
    }

    private void addOutgoingTransition(int state) {
        int target = state;

        while (target == state) {
            target = random.nextInt(numStates);
        }

        DistributionBuilder builder = Distributions.defaultBuilder();
        builder.add(target, 1);
        mdpMec.addChoice(state, Action.of(builder.build()));
    }

    private void initialiseStateVariables(int numStates) {
        this.numStates = numStates;
        mdpMec = new MarkovDecisionProcess();
        mdpMec.addStates(numStates);

        statesList = IntStream.range(0, numStates).boxed().collect(Collectors.toList());
        hasIncomingTransitionsFromOtherState = new Int2BooleanOpenHashMap();
    }
}
