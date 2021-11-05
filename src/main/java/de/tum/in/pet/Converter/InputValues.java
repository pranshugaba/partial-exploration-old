package de.tum.in.pet.Converter;

public class InputValues {
    public final String modulePath;
    public final String constants;
    public final int rewardIndex;

    public InputValues(String modulePath, String constants, int rewardIndex) {
        this.modulePath = modulePath;
        this.constants = constants;
        this.rewardIndex = rewardIndex;
    }
}
