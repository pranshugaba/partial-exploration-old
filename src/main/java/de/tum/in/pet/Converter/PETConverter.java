package de.tum.in.pet.Converter;

import explicit.*;
import prism.PrismException;

import java.io.IOException;

import static de.tum.in.pet.Converter.CTMDPModelConstructor.constructCTMDPModelFromPath;

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
        CTMDP ctmdpModel = constructCTMDPModelFromPath(inputValues.modulePath, inputValues.constants);
        MDP uniformizedModel = new CTMDPUniformizer(ctmdpModel, ctmdpModel.getMaxExitRate()).uniformize();

    }


}
