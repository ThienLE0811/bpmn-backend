package com.example.bpmn.model;

import java.time.LocalDateTime;

/**
 * Scheduling state for a BPMN process whose start event carries a timer - at most one row per
 * {@code processId}, kept in sync with the live BPMN XML by {@code BpmnProcessServiceImpl}.
 * {@code repeatsRemaining}: {@code null} = one-shot ({@code timeDuration}/{@code timeDate}),
 * {@code -1} = unbounded {@code timeCycle}, {@code N > 0} = bounded occurrences left.
 */
public class BpmnProcessStartTimer {
    private String processId;
    private LocalDateTime nextFireAt;
    private Integer repeatsRemaining;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BpmnProcessStartTimer() {
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public LocalDateTime getNextFireAt() {
        return nextFireAt;
    }

    public void setNextFireAt(LocalDateTime nextFireAt) {
        this.nextFireAt = nextFireAt;
    }

    public Integer getRepeatsRemaining() {
        return repeatsRemaining;
    }

    public void setRepeatsRemaining(Integer repeatsRemaining) {
        this.repeatsRemaining = repeatsRemaining;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
