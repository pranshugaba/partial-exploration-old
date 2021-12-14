package de.tum.in.pet.LongRunningTask;

public class TimedTask {
    private final LRTask lrTask;
    private final long timeout;

    public TimedTask(LRTask lrTask, long timeout) {
        this.lrTask = lrTask;
        this.timeout = timeout;
    }

    public void start() {
        LRTaskStoppingThread thread = new LRTaskStoppingThread(lrTask, timeout - System.currentTimeMillis());
        // This call will lead to LRTaskStoppingThread's run method
        thread.start();

        // Long-running code happening here. If it takes too long, it will get stopped by LRTaskStoppingThread
        lrTask.LR_run();

        // Task might have ended without interruption by this thread. In that case, we terminate the thread.
        thread.interrupt();
    }
}
