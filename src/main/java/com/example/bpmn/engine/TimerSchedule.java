package com.example.bpmn.engine;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pure "when does this node's timer next fire / how many times" calculation, shared by every
 * timer flavor that carries {@code timerDuration}/{@code timerDate}/{@code timerCycle} on a
 * {@link BpmnNode} - boundary timers, standalone intermediate catch timers, and timer start
 * events all resolve the same way.
 */
public final class TimerSchedule {
    private TimerSchedule() {
    }

    /** Computes when {@code timerNode}'s timer should next fire, relative to {@code now}. */
    public static LocalDateTime computeNextFireAt(BpmnNode timerNode, LocalDateTime now) {
        if (timerNode.getTimerDate() != null) {
            return LocalDateTime.parse(timerNode.getTimerDate());
        }
        if (timerNode.getTimerDuration() != null) {
            return now.plus(Duration.parse(timerNode.getTimerDuration()));
        }
        return now.plus(TimerCycle.parse(timerNode.getTimerCycle()).interval());
    }

    /** {@code null} if not a {@code timeCycle} timer (one-shot); {@code -1} if unbounded; else the bounded repeat count. */
    public static Integer computeInitialRepeats(BpmnNode timerNode) {
        if (timerNode.getTimerCycle() == null) {
            return null;
        }
        Integer repeatCount = TimerCycle.parse(timerNode.getTimerCycle()).repeatCount();
        return repeatCount != null ? repeatCount : -1;
    }
}
