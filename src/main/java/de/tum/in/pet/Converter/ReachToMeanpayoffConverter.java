package de.tum.in.pet.Converter;

import de.tum.in.pet.util.CliHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.ast.ModulesFile;
import prism.*;
import prism.PrismException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;


public class ReachToMeanpayoffConverter {
    public static void main(String... args) throws IOException, PrismException {
        // parsing arguments
        Option modelOption = new Option("m", "model", true, "Path to model file");
        Option destinationPathOption = new Option("o", "output", true, "Path to output file");
        Option constantOptions = new Option("c", "const", true, "Constants of model/property file, comma separated list");
        Option targetStateNameOption = new Option("t", "target", true, "Name of the target state (Default is \"target\")");

        modelOption.setRequired(true);

        Options options = new Options()
                .addOption(modelOption)
                .addOption(destinationPathOption)
                .addOption(constantOptions)
                .addOption(targetStateNameOption);

        CommandLine commandLine = CliHelper.parse(options, args);

        String filename = commandLine.getOptionValue("m");
        String outputFile = commandLine.hasOption("o") ? commandLine.getOptionValue("o") : filename + "_updated.prism";
        String target_state_label = commandLine.hasOption("t") ? commandLine.getOptionValue("t") : "target";

        // write logs to stdout
        PrismLog mainLog = new PrismFileLog("stdout");

        // Initialise PRISM engine
        Prism prism = new Prism(mainLog);
        prism.initialise();

        // Parse and load a PRISM model from a file
        ModulesFile model = prism.parseModelFile(new File(filename));
        prism.loadPRISMModel(model);

        if (commandLine.hasOption("c")) {
            String constantsString = commandLine.getOptionValue("c");
            UndefinedConstants constants = new UndefinedConstants(model, null);
            if (constantsString == null) {
                constants.checkAllDefined();
            } else {
                constants.defineUsingConstSwitch(constantsString);
            }
            model.setSomeUndefinedConstants(constants.getMFConstantValues());
            constants.iterateModel();
        }

        // Export the states of the model to a file
        String exportStatesFilename =  filename + "_adv.sta";
        String exportLabelsFilename =  filename + "_adv.lab";
        String exportTransitionsFilename =  filename + "_adv.tar";
        prism.exportStatesToFile(Prism.EXPORT_PLAIN, new File(exportStatesFilename));
        prism.exportLabelsToFile(null, Prism.EXPORT_PLAIN, new File(exportLabelsFilename));
        prism.exportTransToFile(false, Prism.EXPORT_PLAIN, new File(exportTransitionsFilename));

        prism.closeDown();

        // create map of all states
        Map<Integer, Boolean> state_map = new HashMap<>();
        {
            File states_file = new File(exportStatesFilename);
            Scanner states_file_reader = new Scanner(states_file);
            // skip first line. Has variable names
            if (states_file_reader.hasNextLine()) {
                states_file_reader.nextLine();
            } else {
                assert false;
            }

            while (states_file_reader.hasNextLine()) {
                String line = states_file_reader.nextLine();
                int state_num = Integer.parseInt(line.split(":")[0]);
                state_map.put(state_num, false);
            }

            states_file_reader.close();
            boolean r = states_file.delete();
            assert r;
        }

        // update map from state (int) to target (bool)
        File labels_file = new File(exportLabelsFilename);
        int targetLabelIndex = -1;
        int initialLabelIndex = -1;
        int initialState = -1; // FIXME: gets only 1 initial state, what if there are more then 1 initial state
        {
            Scanner labels_file_reader = new Scanner(labels_file);
            if (labels_file_reader.hasNextLine()) {
                String line = labels_file_reader.nextLine();
                for (var i : line.split(" ")) {
                    String[] indexNamePair = i.split("=");
                    assert indexNamePair.length == 2;
                    if (indexNamePair[1].equals("\"" + target_state_label + "\"")) {
                        targetLabelIndex = Integer.parseInt(indexNamePair[0]);
                    }
                    if (indexNamePair[1].equals("\"init\"")) {
                        initialLabelIndex = Integer.parseInt(indexNamePair[0]);
                    }
                }
            } else {
                assert false;
            }
            assert targetLabelIndex != -1;
            assert initialLabelIndex != -1;
            assert targetLabelIndex != initialLabelIndex;

            while (labels_file_reader.hasNextLine()) {
                String line = labels_file_reader.nextLine();
                String[] stateLabelPair = line.split(": ");
                assert stateLabelPair.length == 2;
                int state = Integer.parseInt(stateLabelPair[0]);
                for (var i: stateLabelPair[1].split(" ")) {
                    int label = Integer.parseInt(i);
                    if (label == targetLabelIndex)
                        state_map.put(state, true);
                    if (label == initialLabelIndex)
                        initialState = state;
                }
            }

            labels_file_reader.close();
            boolean r = labels_file.delete();
            assert r;
        }

        assert initialState != -1;

        // create updated transition matrix
        StringBuilder transitionMatrix = new StringBuilder();

        // enter self loop transitions (i.e. target state)
        for (Map.Entry<Integer, Boolean> i : state_map.entrySet()) {
            if (i.getValue()) {
                int state = i.getKey();
                transitionMatrix.append(state).append(" 0 ").append(state).append(" 1 target_self_loop\n");
            }
        }

        {
            File transition_file = new File(exportTransitionsFilename);
            Scanner transition_file_reader = new Scanner(transition_file);
            if (transition_file_reader.hasNextLine()) {
                // skip first line. Has (state_count, ???, transition_count)
                transition_file_reader.nextLine();
            } else {
                assert false;
            }

            while (transition_file_reader.hasNextLine()) {
                String line = transition_file_reader.nextLine();
                String[] lineContent = line.split(" ");
                int from_state = Integer.parseInt(lineContent[0]);
                if (state_map.get(from_state))
                    continue;
                transitionMatrix.append(line).append("\n");
            }

            transition_file_reader.close();
            boolean r = transition_file.delete();
            assert r;
        }

        exportConvertedMDP(initialState, transitionMatrix.toString(), outputFile, state_map.size(), state_map);
        System.out.println("Written file to " + outputFile);
    }

    private static Map<Integer, Map<Integer, Map<Integer, Double>>> create_transitions(String transitionMatrix) {
        Map<Integer, Map<Integer, Map<Integer, Double>>> stateTransitions = new HashMap<>();

        String[] transitions = transitionMatrix.split("\n");

        for (String transition : transitions) {
            String[] values = transition.split(" ");
            assert values.length >= 4;
            int from_state = Integer.parseInt(values[0]);
            int action = Integer.parseInt(values[1]);
            int to_state = Integer.parseInt(values[2]);
            double probability = Double.parseDouble(values[3]);
            if (stateTransitions.containsKey(from_state)) {
                Map<Integer, Map<Integer, Double>> actions_map = stateTransitions.get(from_state);
                if (actions_map.containsKey(action)) {
                    Map<Integer, Double> probability_map = actions_map.get(action);
                    probability_map.put(to_state, probability);
                } else {
                    Map<Integer, Double> probability_map = new HashMap<>();
                    probability_map.put(to_state, probability);
                    actions_map.put(action, probability_map);
                }
            } else {
                Map<Integer, Map<Integer, Double>> actions_map = new HashMap<>();
                Map<Integer, Double> probability_map = new HashMap<>();
                probability_map.put(to_state, probability);
                actions_map.put(action, probability_map);
                stateTransitions.put(from_state, actions_map);
            }
        }

//        System.out.println("stateTransitionsMap: " + stateTransitions);

        return  stateTransitions;
    }

    private static String create_transition_string(Map<Integer, Map<Integer, Map<Integer, Double>>> transitions) {
        StringBuilder r = new StringBuilder();
        for (Map.Entry<Integer, Map<Integer, Map<Integer, Double>>> from_state: transitions.entrySet()) {
            for (Map.Entry<Integer, Map<Integer, Double>> action: from_state.getValue().entrySet()) {
                r.append("    [] (s=").append(from_state.getKey()).append(") -> ");
                for (Map.Entry<Integer, Double> to_state: action.getValue().entrySet()) {
                    r.append(to_state.getValue()).append(" : (s'=").append(to_state.getKey()).append(") + ");
                }
                r = new StringBuilder(r.substring(0, r.length() - 3) + ";\n");
            }
        }
        r.append("\n");
        
        return r.toString();
    }

    private static String create_rewards(Map<Integer, Boolean> state_map) {
        StringBuilder r = new StringBuilder();
        for (Map.Entry<Integer, Boolean> i: state_map.entrySet()) {
            if (i.getValue()) {
                r.append("    s=").append(i.getKey()).append(" : 1;\n");
            }
        }
        return r.toString();
    }

    private static String create_target_label(Map<Integer, Boolean> state_map) {
        StringBuilder r = new StringBuilder();
        r.append("label \"target\" = ");
        for (Map.Entry<Integer, Boolean> i: state_map.entrySet()) {
            if (i.getValue()) {
                r.append("(s=").append(i.getKey()).append(") | ");
            }
        }
        return r.substring(0, r.length() - 3) + ";\n";
    }

    private static void exportConvertedMDP(int initialState, String transitionMatrixString, String filename,
                                           int numStates, Map<Integer, Boolean> state_map) throws IOException {
        StringBuilder r = new StringBuilder();
        r
            .append("mdp\n\n")
            .append("module M\n")
            .append("    s: [0..")
            .append(numStates)
            .append("] init ")
            .append(initialState)
            .append(";\n\n")
            .append(create_transition_string(create_transitions(transitionMatrixString)))
            .append("endmodule\n\n")
            .append("rewards\n")
            .append(create_rewards(state_map))
            .append("endrewards\n\n")
            .append(create_target_label(state_map))
            .append("\n");

        // System.out.println(r);

        // writing file
        FileWriter outFile = new FileWriter(filename);
        outFile.write(r.toString());
        outFile.close();
    }

}
