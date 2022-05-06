package de.tum.in.pet.Converter;

import de.tum.in.pet.util.CliHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import prism.PrismException;

import java.io.IOException;

public class InputParser {
    public static final Option modelOption = new Option("m", "model", true, "Path to model file");
    public static final Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
    public static final Option rewardModuleOption = new Option(null, "rewardModule", true, "Name of the reward module in the model file.");
    public static final Option outputFilePathOption = new Option("o", "outputPath", true, "Path to store output file. Files/Directories will be created if not present");

    private String modelPath = null;
    private String constantsString = null;
    private String rewardStructure = null;
    private String outputFilePath = null;

    public InputParser() {
        modelOption.setRequired(true);
//        outputFilePathOption.setRequired(true);
    }


    public InputValues parseUserInput(String[] args) throws PrismException, IOException {
        CommandLine commandLine = parseArgs(args);
        extractOptionValues(commandLine);
        return new InputValues(modelPath, constantsString, rewardStructure, outputFilePath);
    }

    private CommandLine parseArgs(String[] args) {
        Options options = getOptions();
        return CliHelper.parse(options, args);
    }

    private void extractOptionValues(CommandLine commandLine) throws PrismException, IOException {
        modelPath = extractModulesPath(commandLine);
        constantsString = extractConstantsString(commandLine);
        rewardStructure = extractRewardStructure(commandLine);
        outputFilePath = extractOutputFilePath(commandLine);
    }

    private Options getOptions() {
        return new Options()
                .addOption(modelOption)
                .addOption(constantsOption)
                .addOption(rewardModuleOption)
                .addOption(outputFilePathOption);
    }

    private String extractConstantsString(CommandLine commandLine) {
        return commandLine.hasOption(constantsOption.getLongOpt())
                ? commandLine.getOptionValue(constantsOption.getLongOpt())
                : null;
    }

    private String extractModulesPath(CommandLine commandLine) {
        return commandLine.getOptionValue(modelOption.getLongOpt());
    }

    private String extractRewardStructure(CommandLine commandLine) {
        return commandLine.hasOption(rewardModuleOption.getLongOpt())
                ? commandLine.getOptionValue(rewardModuleOption.getLongOpt())
                : null;
    }

    private String extractOutputFilePath(CommandLine commandLine) {
        return commandLine.getOptionValue(outputFilePathOption.getLongOpt());
    }
}
