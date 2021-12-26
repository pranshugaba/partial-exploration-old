package de.tum.in.pet.util;

import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.Converter.CTMDPModelConstructor;
import de.tum.in.pet.Converter.InputParser;
import de.tum.in.pet.Converter.InputValues;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.DistributionBuilder;
import de.tum.in.probmodels.model.Distributions;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import explicit.CTMDP;
import explicit.MDP;
import org.jfree.util.Log;
import prism.PrismException;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CTMDPModelInfo {
    public static void main(String[] args) throws PrismException, IOException {
        InputValues inputValues = new InputParser().parseUserInput(args);
        CTMDPModelConstructor modelConstructor = new CTMDPModelConstructor();
        CTMDP ctmdpModel = modelConstructor.constructCTMDPFromInput(inputValues);
        findMecSizes(ctmdpModel);
    }

    private static void findMecSizes(CTMDP ctmdpModel) {
        int numStates = ctmdpModel.getNumStates();
        MarkovDecisionProcess mdp = new MarkovDecisionProcess();
        mdp.addStates(numStates);

        int maxSuccessors = 0;
        for (int state = 0; state < numStates; state++) {
            for (int choice = 0; choice < ctmdpModel.getNumChoices(state); choice++) {
                int numSuccessors = ctmdpModel.getNumTransitions(state, choice);
                if (maxSuccessors < numSuccessors) {
                    maxSuccessors = numSuccessors;
                }


                DistributionBuilder builder = Distributions.defaultBuilder();
                ctmdpModel.forEachTransition(state, choice, new MDP.TransitionConsumer() {
                    @Override
                    public void accept(int i, int i1, double v) {
                        builder.add(i1, v);
                    }
                });
                mdp.addChoice(state, Action.of(builder.build(), ctmdpModel.getAction(state, choice)));
            }
        }


        mdp.addInitialState(ctmdpModel.getFirstInitialState());
        //Find components
        MecComponentAnalyser analyser = new MecComponentAnalyser();
        BoundedNatBitSet set = NatBitSets.boundedFullSet(numStates);
        List<NatBitSet> mecs = analyser.findComponents(mdp, set);
        int maxSize = 0;
        int maxSuccessorsInMec = 0;
        List<Mec> mecList = mecs.stream().map(mec -> Mec.create(mdp, mec)).collect(Collectors.toList());
        for (Mec mec : mecList) {
            int numPairs = 0;
            int maxS = 0;
            for (Integer state : mec.states) {
                int numActions = mec.actions.get(state.intValue()).size();
                for (Integer action : mec.actions.get(state.intValue())) {
                    int actionSuccessors = mdp.getNumTransitions(state, action);
                    if (maxS < actionSuccessors) {
                        maxS = actionSuccessors;
                    }
                }
                numPairs += numActions;
            }

            if (maxSize < numPairs) {
                maxSize = numPairs;
            }

            if (maxSuccessorsInMec < maxS) {
                maxSuccessorsInMec = maxS;
            }
        }

        Logger logger = Logger.getLogger("CTMDP Model Info");
        logger.log(Level.INFO, "Number of components: " + mecList.size());
        logger.log(Level.INFO, "Max state action pairs in a MEC: " + maxSize);
        logger.log(Level.INFO, "Max successors in a MEC: " + maxSuccessorsInMec);
        logger.log(Level.INFO, "Maximum number of successors in model: " + maxSuccessors);
    }
}
