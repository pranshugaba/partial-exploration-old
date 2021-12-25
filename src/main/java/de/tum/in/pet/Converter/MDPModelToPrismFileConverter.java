package de.tum.in.pet.Converter;

import explicit.MDP;

import java.io.*;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;

/**
 * Writes an MDP model into a prism file. Lists all the states and it's transitions.
 * If rewardGenerator is specified it also writes non-zero rewards of every state and state-action pair.
 */
public class MDPModelToPrismFileConverter {
    private final File targetFile;
    private final MDP mdpModel;
    private final RewardProperty rewardFunctions;

    private BufferedOutputStream bufferedOutputStream;

    public MDPModelToPrismFileConverter(File targetFile, MDP mdp, RewardProperty rewardFunctions){
       this.targetFile = targetFile;
       this.mdpModel = mdp;
       this.rewardFunctions = rewardFunctions;
    }

    public void safeWriteModel(){
        try {
            forceNewTargetFile();
            openOutputStream();
            writeModel();
            closeOutputStream();
        } catch (Exception e) {

            //Closing buffer stream might also throw errors.
            try {
                closeOutputStream();
            } catch (Exception streamException) {
                streamException.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private void writeModel() throws IOException {
        writeModelType();
        newLines(2);
        openModule();
        newLines(2);
        declareStates();
        newLines(1);
        writeTransitions();
        newLines(2);
        closeModule();
        newLines(2);

        if (rewardFunctions != null) {
            openRewardStructure();
            newLines(2);
            writeRewards();
            newLines(2);
            closeRewardStructure();
        }
    }

    private void writeModelType() throws IOException {
        writeToBuffer("mdp");
    }

    private void newLines(int n) throws IOException {
        for (int i = 0; i < n; i++) {
            writeToBuffer("\n");
        }
    }

    private void openModule() throws IOException {
        String moduleName = "default";
        writeToBuffer("module " + moduleName);
    }

    private void declareStates() throws IOException {
        int range = mdpModel.getNumStates() - 1;

        // TODO Multiple initial states?
        int firstInitialState = mdpModel.getFirstInitialState();
        String variable = "s: [0.." + range + "] init " + firstInitialState + ";";
        writeToBuffer(variable);
    }

    private void writeTransitions() throws IOException {
        int numStates = mdpModel.getNumStates();
        for (int state = 0; state < numStates; state++) {
            for (int choice = 0; choice < mdpModel.getNumChoices(state); choice++) {
                writeTransition(state, choice);
                newLines(1);
            }
        }
    }

    private void writeTransition(int state, int choice) throws IOException {
        Iterator<Map.Entry<Integer, Double>> transitionIterator = mdpModel.getTransitionsIterator(state, choice);
        StringBuilder transitionString = new StringBuilder();

        //Action label and state name
        Object actionLabel = mdpModel.getAction(state, choice);
        String actionLabelString = actionLabel == null ? "" : actionLabel.toString();
        transitionString
                .append("[")
                .append(actionLabelString)
                .append("] s=")
                .append(state)
                .append(" -> ");

        // Transitions
        while (transitionIterator.hasNext()) {
            Map.Entry<Integer, Double> transition = transitionIterator.next();
            int target = transition.getKey();
            double probability = transition.getValue();

            transitionString
                    .append(probability)
                    .append(":(s'=")
                    .append(target)
                    .append(")");


            if (transitionIterator.hasNext()) {
                transitionString.append(" + ");
            }
        }

        transitionString.append(";");

        writeToBuffer(transitionString.toString());
    }

    private void closeModule() throws IOException {
        writeToBuffer("endmodule");
    }

    private void openRewardStructure() throws IOException {
        writeToBuffer("rewards \"default_reward\"");
    }

    private void writeRewards() throws IOException {
        int numStates = mdpModel.getNumStates();
        for (int state = 0; state < numStates; state++) {
            writeStateReward(state);
            for (int choice = 0; choice < mdpModel.getNumChoices(state); choice++) {
                writeTransitionReward(state, choice);
            }
        }
    }

    private void writeStateReward(int state) throws IOException {
        double stateReward = rewardFunctions.getStateReward(state);

        if (stateReward == 0d)
            return;

        String stateFormula = "s=" + state;
        writeToBuffer(stateFormula + " : " + stateReward + ";");
        newLines(1);
    }

    private void writeTransitionReward(int state, int choice) throws IOException {
        Object actionLabel = mdpModel.getAction(state, choice);
        double transitionReward = rewardFunctions.getTransitionReward(state, choice, actionLabel);

        if (transitionReward == 0d)
            return;

        String stateFormula = "s=" + state;
        String actionLabelString = actionLabel == null ? "" : actionLabel.toString();

        String transitionRewardString = "[" +
                actionLabelString +
                "] " +
                stateFormula +
                " : " +
                transitionReward +
                ";";

        writeToBuffer(transitionRewardString);
        newLines(1);
    }

    private void closeRewardStructure() throws IOException {
        writeToBuffer("endrewards");
    }

    private void forceNewTargetFile() throws IOException {
        Files.deleteIfExists(targetFile.toPath());
        if (targetFile.getParentFile() != null) {
            Files.createDirectories(targetFile.getParentFile().toPath());
        }
        Files.createFile(targetFile.toPath());
    }

    private void openOutputStream() throws FileNotFoundException {
        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
    }

    private void closeOutputStream() throws IOException {
        if (bufferedOutputStream != null) {
            bufferedOutputStream.close();
        }
    }

    private void writeToBuffer(String string) throws IOException {
        bufferedOutputStream.write(string.getBytes());
    }

    public interface RewardProperty {
        double getStateReward(int s);
        double getTransitionReward(int state, int actionIndex, Object actionLabel);
    }
}
