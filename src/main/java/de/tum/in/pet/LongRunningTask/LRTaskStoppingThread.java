package de.tum.in.pet.LongRunningTask;

public class LRTaskStoppingThread extends Thread{
    private final LRTask lrTask;
    private final long sleepTime;

    public LRTaskStoppingThread(LRTask lrTask, long sleepTime) {
        this.lrTask = lrTask;
        this.sleepTime = sleepTime;
    }

    /**
     * This method will not call super.
     *
     * Let's say this thread sleeps for 2 mins. After 2 mins the the task will get stopped.
     * If the task itself ends before 2 mins, the TimedTask class will raise an interrupt, this will cause an interrupt exception.
     * Then we again stop the task (Not needed, but for safety).
     */
    @Override
    public void run() {
        try {
            Thread.sleep(sleepTime);
            lrTask.LR_stop();
        } catch (InterruptedException e) {
            lrTask.LR_stop();
        }
    }
}
