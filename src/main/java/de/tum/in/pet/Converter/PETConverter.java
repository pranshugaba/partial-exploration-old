package de.tum.in.pet.Converter;

import de.tum.in.probmodels.generator.RewardGenerator;
import explicit.*;
import parser.State;
import prism.PrismException;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PETConverter {
    public static void main(String[] args) {
        try {
            ctmdp2Dtmdp(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void ctmdp2Dtmdp(String[] args) throws PrismException, IOException {
        InputValues inputValues = new InputParser().parseUserInput(args);
        CTMDPModelConstructor modelConstructor = new CTMDPModelConstructor();
        CTMDP ctmdpModel = modelConstructor.constructCTMDPFromInput(inputValues);
        RewardGenerator<State> rewardGenerator = modelConstructor.getRewardGenerator();
        List<State> statesList = modelConstructor.getStatesList();
        MDP uniformizedModel = new CTMDPUniformizer(ctmdpModel, ctmdpModel.getMaxExitRate()).uniformize();
        File targetFile = new File(inputValues.outputFilePath);
        MDPModelToPrismFileConverter fileWriter = new MDPModelToPrismFileConverter(targetFile, uniformizedModel, rewardGenerator, statesList);
        fileWriter.safeWriteModel();
    }
}
