package de.tum.in.pet.Converter;

import de.tum.in.probmodels.generator.PrismRewardGenerator;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.util.PrismHelper;
import explicit.CTMDPSimple;
import explicit.ConstructModel;
import parser.State;
import parser.ast.ModulesFile;
import prism.ModelGenerator;
import prism.Prism;
import prism.PrismDevNullLog;
import prism.PrismException;
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
        int rewardIndex;

        if (inputValues.rewardStructure == null) {
            rewardIndex = 0;
        } else {
            rewardIndex = modelGenerator.getRewardStructIndex(inputValues.rewardStructure);
        }

        rewardGenerator = new PrismRewardGenerator(rewardIndex, modelGenerator);
        return ctmdpSimple;
    }

    private CTMDPSimple constructCTMDPFromModulesFile(ModulesFile modulesFile) throws PrismException {
        Prism prism = new Prism(new PrismDevNullLog());
        ModelGenerator modelGenerator = new ModulesFileModelGenerator(modulesFile, prism);
        ConstructModel constructModel = new ConstructModel(prism);
        CTMDPSimple ctmdpSimple =  (CTMDPSimple) constructModel.constructModel(modelGenerator);
        statesList = constructModel.getStatesList();
        return ctmdpSimple;
    }

    private ModelGenerator getGenerator(PrismHelper.PrismParseResult prismParseResult) throws PrismException{
        ModulesFile modulesFile = prismParseResult.modulesFile();

        Prism prism = new Prism(new PrismDevNullLog());

        return new ModulesFileModelGenerator(modulesFile, prism);
    }

    public RewardGenerator<State> getRewardGenerator() {
        return rewardGenerator;
    }

    public List<State> getStatesList() {
        return statesList;
    }
}
