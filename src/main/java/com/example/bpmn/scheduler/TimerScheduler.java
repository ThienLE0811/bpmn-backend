package com.example.bpmn.scheduler;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically fires due boundary timer events by calling {@link TaskService#processDueTimers()}. */
public class TimerScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TimerScheduler.class);

    private final ScheduledExecutorService executor;

    private TimerScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    public static TimerScheduler start(TaskService taskService) {
        long intervalSeconds = Long.parseLong(AppConfig.getProperty("timer.poll.interval.seconds", "30"));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "timer-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            try {
                taskService.processDueTimers();
            } catch (Exception e) {
                // A ScheduledExecutorService silently stops future runs if the task throws -
                // must never let an exception escape this Runnable.
                logger.error("Unexpected error while processing due timers", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        logger.info("Timer scheduler started (polling every {}s)", intervalSeconds);
        return new TimerScheduler(executor);
    }

    public void stop() {
        executor.shutdown();
    }
}
