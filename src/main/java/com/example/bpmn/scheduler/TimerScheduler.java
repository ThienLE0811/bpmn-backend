package com.example.bpmn.scheduler;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.service.ProcessInstanceService;
import com.example.bpmn.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically fires due boundary/intermediate timer events ({@link TaskService#processDueTimers()}) and timer start events ({@link ProcessInstanceService#processDueStartTimers()}). */
public class TimerScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TimerScheduler.class);

    private final ScheduledExecutorService executor;

    private TimerScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    public static TimerScheduler start(TaskService taskService, ProcessInstanceService processInstanceService) {
        long intervalSeconds = Long.parseLong(AppConfig.getProperty("timer.poll.interval.seconds", "30"));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "timer-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            // Each call is independently guarded - a ScheduledExecutorService silently stops
            // future runs if the task throws, and one kind of timer failing shouldn't block the other.
            try {
                taskService.processDueTimers();
            } catch (Exception e) {
                logger.error("Unexpected error while processing due task timers", e);
            }
            try {
                processInstanceService.processDueStartTimers();
            } catch (Exception e) {
                logger.error("Unexpected error while processing due start timers", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        logger.info("Timer scheduler started (polling every {}s)", intervalSeconds);
        return new TimerScheduler(executor);
    }

    public void stop() {
        executor.shutdown();
    }
}
