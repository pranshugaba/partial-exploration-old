package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.values.Bounds;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import prism.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class ResultWriter {

    public static void write(CommandLine commandLine, List<Pair<Long, Bounds>> timeVBound,
                             List<String> additionalWriteInfo) throws IOException {
        String filename = "temp.txt";
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        StringBuilder modelDetails = new StringBuilder();

        Option[] options = commandLine.getOptions();

        for (Option option : options) {
            String shortOption = option.getOpt();
            String longOption = option.getLongOpt();
            if (shortOption!=null && commandLine.hasOption(shortOption)) {
                modelDetails.append("-").append(shortOption).append(" ");
                if (option.hasArg()) {
                    modelDetails.append(commandLine.getOptionValue(shortOption)).append(" ");
                }
            }
            else if (longOption!=null && commandLine.hasOption(longOption)) {
                modelDetails.append("--").append(longOption).append(" ");
                if (option.hasArg()) {
                    modelDetails.append(commandLine.getOptionValue(longOption)).append(" ");
                }
            }
        }

        writer.write(modelDetails.toString());
        writer.newLine();

        StringBuilder times = new StringBuilder();
        StringBuilder lowerBounds = new StringBuilder();
        StringBuilder upperBounds = new StringBuilder();

        for (Pair<Long, Bounds> timeBounds: timeVBound){
            times.append(timeBounds.first).append(" ");
            lowerBounds.append(timeBounds.second.lowerBound()).append(" ");
            upperBounds.append(timeBounds.second.upperBound()).append(" ");
        }

        writer.write(times.toString());
        writer.newLine();
        writer.write(lowerBounds.toString());
        writer.newLine();
        writer.write(upperBounds.toString());
        writer.newLine();

        for (String info : additionalWriteInfo) {
            writer.write(info);
            writer.newLine();
        }

        writer.close();
    }
}
