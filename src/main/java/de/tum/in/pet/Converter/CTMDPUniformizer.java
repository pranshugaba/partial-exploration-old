package de.tum.in.pet.Converter;

import explicit.CTMDP;
import explicit.Distribution;
import explicit.MDPSimple;

import java.util.Iterator;
import java.util.Map;


public class CTMDPUniformizer {

    private final CTMDP ctmdp;
    private final double maxRate;
    private final MDPSimple mdpSimple;

    CTMDPUniformizer(CTMDP ctmdp, double maxRate) {
        this.ctmdp = ctmdp;
        this.maxRate = maxRate;
        mdpSimple = new MDPSimple(ctmdp.getNumStates());
    }

    public MDPSimple uniformize() {
        int numStates = ctmdp.getNumStates();
        ctmdp.getInitialStates().forEach(mdpSimple::addInitialState);

        for (int state = 0; state < numStates; state++) {
            for (int choice=0; choice<ctmdp.getNumChoices(state); choice++) {
                addUniformizedStateAction(state, choice);
            }
        }

        return mdpSimple;
    }

    private void addUniformizedStateAction(int state, int choice) {
        Distribution uniformizedDistribution = getUniformizedStateAction(state, choice);
        mdpSimple.addActionLabelledChoice(state, uniformizedDistribution, ctmdp.getAction(state, choice));
    }

    private Distribution getUniformizedStateAction(int state, int choice) {
        Distribution distribution = new Distribution();

        int stateActionRate = getStateActionRate(state, choice);

        Iterator<Map.Entry<Integer, Double>> transitionIterator;
        transitionIterator = ctmdp.getTransitionsIterator(state, choice);

        double rateRatio = stateActionRate/maxRate;

        while (transitionIterator.hasNext()) {
            Map.Entry<Integer, Double> transition = transitionIterator.next();
            int targetState = transition.getKey();
            double transitionRate = transition.getValue();
            double probability = transitionRate/stateActionRate;

            double uniformizedRate = rateRatio * probability;
            if (targetState == state) {
                uniformizedRate += 1 - rateRatio;
            }

            distribution.add(targetState, uniformizedRate);
        }

        if (!distribution.contains(state)) {
            double selfLoopProbability = 1 - rateRatio;
            if (selfLoopProbability > 0) {
                distribution.add(state, selfLoopProbability);
            }
        }

        return distribution;
    }

    private int getStateActionRate(int state, int choice) {
        // Rate for a state action is represented as sum of all the transition probabilities of that state-action pair
        int stateActionRate = 0;
        Iterator<Map.Entry<Integer, Double>> transitionIterator = ctmdp.getTransitionsIterator(state, choice);
        while (transitionIterator.hasNext()) {
            Map.Entry<Integer, Double> transition = transitionIterator.next();
            stateActionRate += transition.getValue();
        }

        if (ctmdp.getNumTransitions(state, choice) > 0) {
            checkRate(stateActionRate);
        }
        return stateActionRate;
    }

    private static void checkRate(int rate) {
        if (rate <= 0) {
            //TODO CHANGE THIS
//            throw new IllegalStateException("Rate should be greater than 0");
        }
    }
}
