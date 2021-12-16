package de.tum.in.pet.SimulateMecPerformanceTest;

import de.tum.in.probmodels.model.MarkovDecisionProcess;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimulateMecExperiment {
    private final List<TimeTaken> timeTakenList = new ArrayList<>();
    private final Logger logger = Logger.getLogger("SimulateMecExperiment");

    public static void main(String[] args) {
        SimulateMecExperiment experiment = new SimulateMecExperiment();
        experiment.run();
    }

    public void run() {
        MdpMecGenerator mecGenerator = new MdpMecGenerator();

        MarkovDecisionProcess MEC5 = mecGenerator.createMec(5);
        MarkovDecisionProcess MEC10 = mecGenerator.createMec(10);
        MarkovDecisionProcess MEC15 = mecGenerator.createMec(15);
        MarkovDecisionProcess MEC20 = mecGenerator.createMec(20);
        MarkovDecisionProcess MEC40 = mecGenerator.createMec(40);
        MarkovDecisionProcess MEC80 = mecGenerator.createMec(80);

        double nSamples = 1e6;

        runSimulateExperiment(MEC5, nSamples);
        log("MEC5 completed");
        runSimulateExperiment(MEC10, nSamples);
        log("MEC10 completed");
        runSimulateExperiment(MEC15, nSamples);
        log("MEC15 completed");
        runSimulateExperiment(MEC20, nSamples);
        log("MEC20 completed");
        runSimulateExperiment(MEC40, nSamples);
        log("MEC40 completed");
        runSimulateExperiment(MEC80, nSamples);
        log("MEC80 completed");

        try {
            writeResults();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runSimulateExperiment(MarkovDecisionProcess MEC, double nSamples) {
        long startTime, endTime;
        long timeTaken1, timeTaken2, timeTaken3;

        RandomAccessMecSimulator randomAccessMecSimulator = new RandomAccessMecSimulator(MEC, nSamples);
        startTime = System.currentTimeMillis();
        randomAccessMecSimulator.simulate();
        endTime = System.currentTimeMillis();
        timeTaken1 = (endTime - startTime)/1000;

        log("Random access method completed");

        HeuristicMecSimulator heuristicMecSimulator = new HeuristicMecSimulator(MEC, nSamples);
        startTime = System.currentTimeMillis();
        heuristicMecSimulator.simulate();
        endTime = System.currentTimeMillis();
        timeTaken2 = (endTime - startTime)/1000;

        log("Heuristic method completed");

        StandardMecSimulator standardMecSimulator = new StandardMecSimulator(MEC, nSamples);
        startTime = System.currentTimeMillis();
        standardMecSimulator.simulate();
        endTime = System.currentTimeMillis();
        timeTaken3 = (endTime - startTime)/1000;

        log("Standard method completed");

        timeTakenList.add(new TimeTaken(timeTaken1, timeTaken2, timeTaken3));
    }

    private void writeResults() throws IOException {
        String filename = "simulationResults.txt";
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        for (TimeTaken timeTaken : timeTakenList) {
            writer.newLine();
            writer.write(String.valueOf(timeTaken.randomAccessTimeTaken));
            writer.newLine();
            writer.write(String.valueOf(timeTaken.heuristicTimeTaken));
            writer.newLine();
            writer.write(String.valueOf(timeTaken.standardTimeTaken));
            writer.newLine();
        }

        writer.close();
    }

    private void log(String s) {
        logger.log(Level.INFO, s);
    }
}
