package de.tum.in.pet.SimulateMecPerformanceTest;

public class TimeTaken {
    public final long randomAccessTimeTaken;
    public final long heuristicTimeTaken;
    public final long standardTimeTaken;


    public TimeTaken(long randomAccessTimeTaken, long heuristicTimeTaken, long standardTimeTaken) {
        this.randomAccessTimeTaken = randomAccessTimeTaken;
        this.heuristicTimeTaken = heuristicTimeTaken;
        this.standardTimeTaken = standardTimeTaken;
    }
}
