package de.tum.in.pet.Converter;

import de.tum.in.probmodels.generator.PrismRewardGenerator;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.util.PrismHelper;
import explicit.CTMDPSimple;
import explicit.Distribution;
import parser.State;
import parser.ast.ModulesFile;
import prism.*;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;
import java.util.List;

public class CTMDPModelConstructor {

    private RewardGenerator<State> rewardGenerator;
    private List<State> statesList;

    public CTMDPSimple constructCTMDPFromInput(InputValues inputValues) throws PrismException, IOException {
        PrismHelper.PrismParseResult prismParseResult = PrismHelper.parse(inputValues.modulePath, null, inputValues.constants);
        ModelGenerator modelGenerator = getGenerator(prismParseResult);
        CTMDPSimple ctmdpSimple =  constructCTMDPFromModulesFile(prismParseResult.modulesFile());
        storeRewardGenerator(inputValues.rewardStructure, modelGenerator);
        return ctmdpSimple;
    }

    private CTMDPSimple constructCTMDPFromModulesFile(ModulesFile modulesFile) throws PrismException {
        Prism prism = new Prism(new PrismDevNullLog());
        ModulesFileModelGenerator modelGenerator = new ModulesFileModelGenerator(modulesFile, prism);
        return constructCTMDP(modelGenerator);
    }

    private CTMDPSimple constructCTMDP(ModulesFileModelGenerator generator) throws PrismException {
        statesList = generator.createVarList().getAllStates();
        int numStates = statesList.size();

        CTMDPSimple model = new CTMDPSimple(numStates);
        model.setStatesList(statesList);

        int numChoices, numTransitions;
        Distribution choiceDistribution;
        String choiceLabel;
        for (int state = 0; state < numStates; state++) {
            generator.exploreState(statesList.get(state));

            numChoices = generator.getNumChoices();

            assert numChoices > 0;
            for (int choice = 0; choice < numChoices; choice++) {
                numTransitions = generator.getNumTransitions(choice);
                choiceDistribution = new Distribution();
                choiceLabel = generator.getTransitionAction(choice);

                assert numTransitions > 0;

                for (int transition = 0; transition < numTransitions; transition++) {
                    State targetState = generator.computeTransitionTarget(choice, transition);
                    int target = statesList.indexOf(targetState);
                    assert target != -1;
                    double prob = generator.getTransitionProbability(choice, transition);

                    choiceDistribution.add(target, prob);
                }

                model.addActionLabelledChoice(state, choiceDistribution, choiceLabel);
            }
        }

        for (State initialState : generator.getInitialStates()) {
            int initialStateIndex = statesList.indexOf(initialState);
            assert initialStateIndex != -1;

            model.addInitialState(initialStateIndex);
        }

        return model;
    }

    private ModelGenerator getGenerator(PrismHelper.PrismParseResult prismParseResult) throws PrismException{
        ModulesFile modulesFile = prismParseResult.modulesFile();

        Prism prism = new Prism(new PrismDevNullLog());

        return new ModulesFileModelGenerator(modulesFile, prism);
    }

    private void storeRewardGenerator(String rewardStructure, ModelGenerator modelGenerator) {
        int rewardIndex;

        if (rewardStructure == null) {
            rewardIndex = 0;
        } else {
            rewardIndex = modelGenerator.getRewardStructIndex(rewardStructure);
        }

        rewardGenerator = new PrismRewardGenerator(rewardIndex, modelGenerator);
    }

    public RewardGenerator<State> getRewardGenerator() {
        return rewardGenerator;
    }

    public List<State> getStatesList() {
        return statesList;
    }
}
