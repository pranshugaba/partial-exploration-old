package de.tum.in.pet.Converter;

import explicit.CTMDP;
import explicit.Distribution;
import explicit.MDPSimple;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
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
        Distribution roundedOff = getRoundedOffDistribution(state, uniformizedDistribution);
        mdpSimple.addActionLabelledChoice(state, roundedOff, ctmdp.getAction(state, choice));
    }

    private Distribution getUniformizedStateAction(int state, int choice) {
        Distribution distribution = new Distribution();

        double stateActionRate = getStateActionRate(state, choice);

        double selfLoopProb = -1;

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
                selfLoopProb = uniformizedRate;
            }

            distribution.add(targetState, uniformizedRate);
        }


        // We haven't added self loop probability, because there is no transition with self loop for this choice
        if (selfLoopProb == -1) {
            selfLoopProb = 1 - rateRatio;
            if (selfLoopProb > 0) {
                distribution.add(state, selfLoopProb);
            }
        }

        return distribution;
    }

    private double getStateActionRate(int state, int choice) {
        // Rate for a state action is represented as sum of all the transition probabilities of that state-action pair
        double stateActionRate = 0;
        Iterator<Map.Entry<Integer, Double>> transitionIterator = ctmdp.getTransitionsIterator(state, choice);
        while (transitionIterator.hasNext()) {
            Map.Entry<Integer, Double> transition = transitionIterator.next();
            stateActionRate += transition.getValue();
        }

        checkRate(stateActionRate);
        return stateActionRate;
    }

    private static void checkRate(double rate) {
        if (rate <= 0) {
            throw new IllegalStateException("Rate should be greater than 0");
        }
    }

    private Distribution getRoundedOffDistribution(int state, Distribution uniformizedDistribution) {
        if (uniformizedDistribution.contains(state)) {
            double prob = uniformizedDistribution.get(state);

            // Because of floating point error, sometimes self loops may have very low probability of the order of
            // e-16. We remove self loop probabilities if they are less than 1e-5
            if (prob < 1e-5) {
                uniformizedDistribution.set(state, 0.0d);
            }
        }

        // Probabilities rounded up to 5 decimal places
        DecimalFormat formatter = new DecimalFormat("#.#####");
        formatter.setRoundingMode(RoundingMode.HALF_UP);

        double sum = 0;

        Distribution roundedOffDistribution = new Distribution();
        for (Map.Entry<Integer, Double> transition : uniformizedDistribution) {
            int target = transition.getKey();
            double prob = transition.getValue();

            double roundedProb = Double.parseDouble(formatter.format(prob));
            sum += roundedProb;
            roundedOffDistribution.add(target, roundedProb);
        }


        // The sum of probabilities should add up to 1
        BigDecimal diff = BigDecimal.ONE.subtract(BigDecimal.valueOf(sum));

        // Sometimes because of floating point error, the sum can be slightly less/more than 1.
        // In that case we just add/subtract the small probability to the first transition.
        if (!diff.equals(BigDecimal.ZERO)) {

            Iterator<Map.Entry<Integer, Double>> supportIterator = roundedOffDistribution.iterator();
            Map.Entry<Integer, Double> firstTransition = supportIterator.next();
            int firstTarget = firstTransition.getKey();
            double firstTransitionProb = firstTransition.getValue();

            roundedOffDistribution.set(firstTarget, BigDecimal.valueOf(firstTransitionProb).add(diff).doubleValue());
        }

        return roundedOffDistribution;
    }
}
