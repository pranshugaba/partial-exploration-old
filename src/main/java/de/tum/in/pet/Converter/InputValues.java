package de.tum.in.pet.Converter;

public class InputValues {
    public final String modulePath;
    public final String constants;
    public final String rewardStructure;
    public final String outputFilePath;

    public InputValues(String modulePath, String constants, String rewardStructure, String outputFilePath) {
        this.modulePath = modulePath;
        this.constants = constants;
        this.rewardStructure = rewardStructure;
        this.outputFilePath = outputFilePath;
    }
}
