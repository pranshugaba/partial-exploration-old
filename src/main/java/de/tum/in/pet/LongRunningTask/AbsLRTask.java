package de.tum.in.pet.LongRunningTask;

public abstract class AbsLRTask implements LRTask {

    // Any changes made to volatile variable, will be visible immediately to any threads that access it.
    protected volatile boolean stopExecution = false;

    @Override
    public void LR_stop() {
        stopExecution = true;
    }
}
