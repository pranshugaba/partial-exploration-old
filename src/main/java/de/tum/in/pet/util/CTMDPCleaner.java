package de.tum.in.pet.util;

// Given an input CTMDP, this class removes the low probability transitions
// Any transition which has value less than 1e-4 will get removed


import de.tum.in.pet.Converter.CTMDPModelConstructor;
import de.tum.in.pet.Converter.InputParser;
import de.tum.in.pet.Converter.InputValues;
import de.tum.in.pet.Converter.MDPModelToPrismFileConverter;
import explicit.CTMDP;
import prism.PrismException;

import java.io.File;
import java.io.IOException;

public class CTMDPCleaner {
    public static void main(String[] args) throws PrismException, IOException {
        InputValues inputValues = new InputParser().parseUserInput(args);
        CTMDPModelConstructor modelConstructor = new CTMDPModelConstructor();
        modelConstructor.setIgnoreLowProbability(true);
        CTMDP ctmdpModel = modelConstructor.constructCTMDPFromInput(inputValues);
        File targetFile = new File(inputValues.outputFilePath);
        MDPModelToPrismFileConverter converter = new MDPModelToPrismFileConverter(targetFile, ctmdpModel, new MDPModelToPrismFileConverter.RewardProperty() {
            @Override
            public double getStateReward(int s) {
                return modelConstructor.getRewardGenerator().stateReward(modelConstructor.getStatesList().get(s));
            }

            @Override
            public double getTransitionReward(int state, int actionIndex, Object actionLabel) {
                return modelConstructor.getRewardGenerator().transitionReward(modelConstructor.getStatesList().get(state), actionLabel);
            }
        });
        converter.safeWriteModel();
    }
}
