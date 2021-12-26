package de.tum.in.pet.mecGenerator;

import de.tum.in.pet.Converter.MDPModelToPrismFileConverter;
import de.tum.in.probmodels.model.MarkovDecisionProcess;

import java.io.File;
import java.util.Random;

public class MecsForExperiment {
    private static final Random random = new Random();
    private static final String baseDirectory = "data/mdpMecModels/";

    public static void main(String[] args) {
        MdpMecGenerator mecGenerator = new MdpMecGenerator();

        MarkovDecisionProcess MEC50 = mecGenerator.createMec(50);
        MarkovDecisionProcess MEC200 = mecGenerator.createMec(200);
        MarkovDecisionProcess MEC400 = mecGenerator.createMec(400);
        MarkovDecisionProcess MEC1000 = mecGenerator.createMec(1000);
        MarkovDecisionProcess MEC2000 = mecGenerator.createMec(2000);
        MarkovDecisionProcess MEC4000 = mecGenerator.createMec(4000);

        writeMdpToFile(MEC50, "mec50.prism");
        writeMdpToFile(MEC200, "mec200.prism");
        writeMdpToFile(MEC400, "mec400.prism");
        writeMdpToFile(MEC1000, "mec1000.prism");
        writeMdpToFile(MEC2000, "mec2000.prism");
        writeMdpToFile(MEC4000, "mec4000.prism");
    }

    public static void writeMdpToFile(MarkovDecisionProcess mdp, String fileName) {
        File targetFile = new File(baseDirectory + fileName);
        MDPModelToPrismFileConverter.RewardProperty property = new MDPModelToPrismFileConverter.RewardProperty() {
            @Override
            public double getStateReward(int s) {
                return ((double) random.nextInt(50))/50d;
            }

            @Override
            public double getTransitionReward(int state, int actionIndex, Object actionLabel) {
                return 0;
            }
        };
        MDPModelToPrismFileConverter converter = new MDPModelToPrismFileConverter(targetFile, mdp, property);
        converter.safeWriteModel();
    }
}
