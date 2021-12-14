package de.tum.in.pet.LongRunningTask.demo;

import de.tum.in.pet.LongRunningTask.TimedTask;

public class SampleLRTaskDemo {
    public static void main(String[] args) {
        //  This SampleLRTask1 will get interrupted and it will end
        SampleLRTask1 task = new SampleLRTask1();
        TimedTask timedTask = new TimedTask(task, System.currentTimeMillis() + 5000);
        timedTask.start();

        // task2 will end sooner. So an interrupt exception will be caught by LRStoppingThread
        SampleLRTask2 task2 = new SampleLRTask2();
        TimedTask timedTask2 = new TimedTask(task2, System.currentTimeMillis() + 4000);
        timedTask2.start();

        // task3 will behave same as that of task1
        SampleLRTask2 task3 = new SampleLRTask2();
        TimedTask timedTask3 = new TimedTask(task3, System.currentTimeMillis() + 500);
        timedTask3.start();
    }
}


