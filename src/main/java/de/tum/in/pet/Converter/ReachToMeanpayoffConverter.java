package de.tum.in.pet.Converter;

import parser.State;
import parser.Values;
import parser.ast.Expression;
import parser.ast.ModulesFile;
import parser.ast.ModulesFile;
import prism.*;
import prism.PrismException;
import simulator.ModulesFileModelGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;


public class ReachToMeanpayoffConverter {
    public static void main(String... args) throws IOException, PrismException {
        // write logs to stdout
        PrismLog mainLog = new PrismFileLog("stdout");

        // Initialise PRISM engine
        Prism prism = new Prism(mainLog);
        prism.initialise();

//        String filename = "tmp/models/n_half_actions.prism";
        String filename = "data/models/ij.3.prism";

        // Parse and load a PRISM model from a file
        ModulesFile model = prism.parseModelFile(new File(filename)); // TODO: get file from args
        prism.loadPRISMModel(model);

        // TODO: set constants from args
//        Values vals = new Values();
//        vals.addValue("N", 5);
//        vals.addValue("dead", 2);
//        prism.setPRISMModelConstants(vals);

        // Export the states of the model to a file
        prism.exportStatesToFile(Prism.EXPORT_PLAIN, new File(filename + "_adv.sta"));
        prism.exportLabelsToFile(null, Prism.EXPORT_PLAIN, new File(filename + "_adv.lab"));
        prism.exportTransToFile(false, Prism.EXPORT_PLAIN, new File(filename + "_adv.tar"));

        // create map of all states
        File states_file = new File(filename + "_adv.sta");
        Scanner states_file_reader = new Scanner(states_file);
        // skip first line. Has variable names
        if (states_file_reader.hasNextLine()) {
            states_file_reader.nextLine();
        } else {
            // FIXME
        }

        Map<Integer, Boolean> state_map = new HashMap();
        while (states_file_reader.hasNextLine()) {
            String line = states_file_reader.nextLine();
            int state_num = Integer.parseInt(line.split(":")[0]);
            state_map.put(state_num, false);
        }

        // update map from state (int) to target (bool)
        File labels_file = new File(filename + "_adv.lab");
        Scanner labels_file_reader = new Scanner(labels_file);
        int targetLabelIndex = -1;
        int initialLabelIndex = -1;
        if (labels_file_reader.hasNextLine()) {
            String line = labels_file_reader.nextLine();
            for (var i: line.split(" ")) {
                String[] indexNamePair = i.split("=");
                assert indexNamePair.length == 2;
                if (indexNamePair[1].equals("\"target\"")) {
                    targetLabelIndex = Integer.parseInt(indexNamePair[0]);
                }if (indexNamePair[1].equals("\"init\"")) {
                    initialLabelIndex = Integer.parseInt(indexNamePair[0]);
                }
            }
        } else {
            // FIXME
        }

        assert targetLabelIndex != -1;
        assert initialLabelIndex != -1;
        assert targetLabelIndex != initialLabelIndex;

        int initialState = -1; // FIXME: gets only 1 initial state, what if there are more then 1 initial state
        while (labels_file_reader.hasNextLine()) {
            String line = labels_file_reader.nextLine();
            String[] stateLabelPair = line.split(": ");
            assert stateLabelPair.length == 2;
            int state = Integer.parseInt(stateLabelPair[0]);
            int label = Integer.parseInt(stateLabelPair[1]);
            if (label == targetLabelIndex)
                state_map.put(state, true);
            if (label == initialLabelIndex)
                initialState = state;
        }

        assert initialState != -1;

        // create updated transition matrix
        String transitionMatrix = "";

        // enter self loop transitions (i.e. target state)
        for (Map.Entry<Integer, Boolean> i: state_map.entrySet()) {
            if (i.getValue()) {
                int state = i.getKey();
                transitionMatrix += state + " 0 " + state + " 1 target_self_loop\n";
            }
        }

        File transition_file = new File(filename + "_adv.tar");
        Scanner transition_file_reader = new Scanner(transition_file);
        if (transition_file_reader.hasNextLine()) {
            // skip first line. Has (state_count, ???, transition_count)
            transition_file_reader.nextLine();
        } else {
            // FIXME
        }

        while (transition_file_reader.hasNextLine()) {
            String line = transition_file_reader.nextLine();
            String[] lineContent = line.split(" ");
            int from_state = Integer.parseInt(lineContent[0]);
            if (state_map.get(from_state))
                continue;
            transitionMatrix += line + "\n";
        }

//        System.out.println("State Map:\n\t" + state_map);
//        System.out.println("Transition Matrix:");
//        System.out.println(transitionMatrix);

        exportConvertedMDP(initialState, transitionMatrix, filename + "_updated.prism", state_map.size(), state_map);
        System.out.println("Written file to " + filename + "_updated.prism");

//        String r = create_transition_string(create_transitions(transitionMatrix));
//        System.out.println("Prism Entry:\n");
//        System.out.println(r);


//        System.out.println("Model Type: " + model.getModelType());
//
//        int _i = 0;
//        System.out.println("Labels:");
//        for (var i: model.getLabelNames())
//            System.out.println("  Label " + ++_i + ": " + i);
//
//        ModulesFileModelGenerator modelGenerator = new ModulesFileModelGenerator(model, prism);
//        System.out.println("Initial state: " + modelGenerator.getInitialState());
//
//        State initialState = modelGenerator.getInitialState();
//        modelGenerator.exploreState(initialState);
//
//        if (modelGenerator.isLabelTrue("target"))
//            System.out.println("State: " + initialState + " is the target state");
//
//        System.out.println("choices: " + modelGenerator.getNumChoices());
//        System.out.println("transitions: " + modelGenerator.getNumTransitions());

    }

    private static Map<Integer, Map<Integer, Map<Integer, Double>>> create_transitions(String transitionMatrix) {
        Map<Integer, Map<Integer, Map<Integer, Double>>> stateTransitions = new HashMap();

        String[] transitions = transitionMatrix.split("\n");

        for (int i = 0; i < transitions.length; i++) {
            String[] values = transitions[i].split(" ");
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
                    Map<Integer, Double> probability_map = new HashMap();
                    probability_map.put(to_state, probability);
                    actions_map.put(action, probability_map);
                }
            } else {
                Map<Integer, Map<Integer, Double>> actions_map = new HashMap();
                Map<Integer, Double> probability_map = new HashMap();
                probability_map.put(to_state, probability);
                actions_map.put(action, probability_map);
                stateTransitions.put(from_state, actions_map);
            }
        }

//        System.out.println("stateTransitionsMap: " + stateTransitions);

        return  stateTransitions;
    }

    private static String create_transition_string(Map<Integer, Map<Integer, Map<Integer, Double>>> transitions) {
        String r = "";
        for (Map.Entry<Integer, Map<Integer, Map<Integer, Double>>> from_state: transitions.entrySet()) {
            for (Map.Entry<Integer, Map<Integer, Double>> action: from_state.getValue().entrySet()) {
                r += "    [] (s=" + from_state.getKey() + ") -> ";
                for (Map.Entry<Integer, Double> to_state: action.getValue().entrySet()) {
                    r += to_state.getValue() + " : (s'=" + to_state.getKey() + ") + ";
                }
                r = r.substring(0, r.length() - 3) + ";\n";
            }
            r += "\n";
        }
        r += "\n";

        return r;
    }

    private static String create_rewards(Map<Integer, Boolean> state_map) {
        String r = "";
        for (Map.Entry<Integer, Boolean> i: state_map.entrySet()) {
            if (i.getValue()) {
                r += "    s=" + i.getKey() + " : 1;\n";
            }
        }
        return r;
    }

    private static String create_target_label(Map<Integer, Boolean> state_map) {
        String r = "";
        r += "label \"target\" = ";
        for (Map.Entry<Integer, Boolean> i: state_map.entrySet()) {
            if (i.getValue()) {
                r += "(s=" + i.getKey() + ") | ";
            }
        }
        return r.substring(0, r.length() - 3) + ";\n";
    }

    private static void exportConvertedMDP(int initialState, String transitionMatrixString, String filename, int numStates, Map<Integer, Boolean> state_map) throws IOException {
        String r = "";
        r += "mdp\n\n";
        r += "module M\n";
        r += "    s: [0.." + numStates + "] init " + initialState  + ";\n\n";
        r +=  create_transition_string(create_transitions(transitionMatrixString));
        r += "endmodule\n\n";
        r += "rewards\n";
        r += create_rewards(state_map);
        r += "endrewards\n\n";
        r += create_target_label(state_map);
        r += "\n";

        System.out.println(r);

        // writing file
        FileWriter outFile = new FileWriter(filename);
        outFile.write(r);
        outFile.close();
    }

}
