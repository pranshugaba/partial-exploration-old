package de.tum.in.pet.LongRunningTask.demo;

import de.tum.in.pet.LongRunningTask.AbsLRTask;

public class SampleLRTask1 extends AbsLRTask {

    @Override
    public void LR_run() {
        // This is an endless loop. It may run forever.
        while (!stopExecution) {
            doSomething();
        }
    }

    private void doSomething() {

    }
}