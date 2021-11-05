package de.tum.in.pet.Converter;

import de.tum.in.probmodels.util.PrismHelper;
import explicit.CTMDPSimple;
import explicit.ConstructModel;
import parser.ast.ModulesFile;
import prism.ModelGenerator;
import prism.Prism;
import prism.PrismDevNullLog;
import prism.PrismException;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;

public class CTMDPModelConstructor {
    public static CTMDPSimple constructCTMDPModelFromPath(String modulesPath, String constantString) throws PrismException, IOException {
        PrismHelper.PrismParseResult prismParseResult = PrismHelper.parse(modulesPath, null, constantString);
        ModulesFile modulesFile = prismParseResult.modulesFile();
        return constructCTMDPFromModulesFile(modulesFile);
    }

    private static CTMDPSimple constructCTMDPFromModulesFile(ModulesFile modulesFile) throws PrismException {
        Prism prism = new Prism(new PrismDevNullLog());
        ModelGenerator modelGenerator = new ModulesFileModelGenerator(modulesFile, prism);
        ConstructModel constructModel = new ConstructModel(prism);
        return (CTMDPSimple) constructModel.constructModel(modelGenerator);
    }
}
