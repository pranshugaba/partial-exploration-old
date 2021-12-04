package de.tum.in.pet.implementation.meanPayoff;

public enum SimulateMec {
    STANDARD, // We simulate mec in the right way, till all the state action pair has been visited nSample times
    CHEAT, // We visit the same state-action pair again and again, till we record nSample visits. Done for every state in Mec
    HEURISTIC // We perform simulation for nSample * numTransitions number of times
}
