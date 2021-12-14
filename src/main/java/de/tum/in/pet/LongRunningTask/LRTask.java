package de.tum.in.pet.LongRunningTask;

/**
 * This is a interface for long-running tasks
 *
 * Tasks extending this, typically takes more time to run. Using this interface and TimedTask service, one can run
 * create and run long-running tasks.
 * run() and stop() are found in many standard interfaces. To avoid naming collision, I named them LR_run and LR_stop.
 *
 */
public interface LRTask {
    void LR_run();
    void LR_stop();
}
