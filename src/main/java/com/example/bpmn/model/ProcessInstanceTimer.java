package com.example.bpmn.model;

import java.time.LocalDateTime;

/** A process instance currently parked at a standalone intermediate catch timer event, waiting for {@code dueDate}. One-shot - the row is deleted once the timer fires. */
public class ProcessInstanceTimer {
    private String id;
    private String processInstanceId;
    private String nodeId;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;

    public ProcessInstanceTimer() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
