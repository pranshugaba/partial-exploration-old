package de.tum.in.pet.Converter;

import de.tum.in.pet.util.CliHelper;
import de.tum.in.probmodels.util.PrismHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.ast.ModulesFile;
import prism.ModelGenerator;
import prism.Prism;
import prism.PrismDevNullLog;
import prism.PrismException;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;

public class InputParser {
    public static final Option modelOption = new Option("m", "model", true, "Path to model file");
    public static final Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
    public static final Option rewardModuleOption = new Option(null, "rewardModule", true, "Name of the reward module in the model file.");
    public static final Option outputFilePathOption = new Option("o", "outputPath", true, "Path to store output file. Files/Directories will be created if not present");

    private String modelPath = null;
    private String constantsString = null;
    private int rewardIndex = 0;
    private String outputFilePath = null;

    InputParser() {
        modelOption.setRequired(true);
        outputFilePathOption.setRequired(true);
    }


    public InputValues parseUserInput(String[] args) throws PrismException, IOException {
        CommandLine commandLine = parseArgs(args);
        extractOptionValues(commandLine);
        return new InputValues(modelPath, constantsString, rewardIndex, outputFilePath);
    }

    private CommandLine parseArgs(String[] args) {
        Options options = getOptions();
        return CliHelper.parse(options, args);
    }

    private void extractOptionValues(CommandLine commandLine) throws PrismException, IOException {
        modelPath = extractModulesPath(commandLine);
        constantsString = extractConstantsString(commandLine);

        ModelGenerator modelGenerator = getGenerator(modelPath, constantsString);
        rewardIndex = extractRewardIndex(modelGenerator, commandLine);
        outputFilePath = extractOutputFilePath(commandLine);
    }

    private Options getOptions() {
        return new Options()
                .addOption(modelOption)
                .addOption(constantsOption)
                .addOption(rewardModuleOption);
    }

    private ModelGenerator getGenerator(String modelPath, String constantsString) throws PrismException, IOException {
        PrismHelper.PrismParseResult prismParseResult = PrismHelper.parse(modelPath, null, constantsString);
        ModulesFile modulesFile = prismParseResult.modulesFile();

        Prism prism = new Prism(new PrismDevNullLog());

        return new ModulesFileModelGenerator(modulesFile, prism);
    }

    private String extractConstantsString(CommandLine commandLine) {
        return commandLine.hasOption(constantsOption.getLongOpt())
                ? commandLine.getOptionValue(constantsOption.getLongOpt())
                : null;
    }

    private String extractModulesPath(CommandLine commandLine) {
        return commandLine.getOptionValue(modelOption.getLongOpt());
    }

    private int extractRewardIndex(ModelGenerator modelGenerator, CommandLine commandLine) {
        return commandLine.hasOption(rewardModuleOption.getLongOpt())
                ? modelGenerator.getRewardStructIndex(commandLine.getOptionValue(rewardModuleOption.getLongOpt()))
                : 0;
    }

    private String extractOutputFilePath(CommandLine commandLine) {
        return commandLine.getOptionValue(outputFilePathOption.getLongOpt());
    }
}
