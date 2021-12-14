package de.tum.in.pet.LongRunningTask.demo;

import de.tum.in.pet.LongRunningTask.AbsLRTask;

public class SampleLRTask2 extends AbsLRTask {
    @Override
    public void LR_run() {
        // sleep for 2 seconds
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
