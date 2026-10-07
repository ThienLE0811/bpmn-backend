package com.example.bpmn.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public class ProcessInstance {
    private String id;
    private String processId;
    private Integer processVersion;
    private String status;
    private String currentNodeId;
    private Map<String, Object> variables;
    private Set<String> pendingJoinArrivals;
    private String incidentNodeId;
    private String incidentMessage;
    private String startedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProcessInstance() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Integer getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(Integer processVersion) {
        this.processVersion = processVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    /** Sequence-flow ids that have arrived at a not-yet-satisfied parallel join gateway. See {@link com.example.bpmn.engine.ProcessEngine}. */
    public Set<String> getPendingJoinArrivals() {
        return pendingJoinArrivals;
    }

    public void setPendingJoinArrivals(Set<String> pendingJoinArrivals) {
        this.pendingJoinArrivals = pendingJoinArrivals;
    }

    /** Node the instance stopped at when its status is FAILED, or null - see {@link #markFailed}. */
    public String getIncidentNodeId() {
        return incidentNodeId;
    }

    public void setIncidentNodeId(String incidentNodeId) {
        this.incidentNodeId = incidentNodeId;
    }

    /** Why the instance failed at {@link #getIncidentNodeId()}, or null. */
    public String getIncidentMessage() {
        return incidentMessage;
    }

    public void setIncidentMessage(String incidentMessage) {
        this.incidentMessage = incidentMessage;
    }

    /**
     * Parks the instance at the node that could not be executed - used when a service task's
     * connector fails, where aborting would throw away a walk of an instance that already exists.
     * Variables keep the values they had before the failed step, so the instance can be resumed
     * from {@code nodeId} once the cause is fixed.
     */
    public void markFailed(String nodeId, String message, LocalDateTime now) {
        this.status = "FAILED";
        this.currentNodeId = nodeId;
        this.incidentNodeId = nodeId;
        this.incidentMessage = message;
        this.updatedAt = now;
    }

    /** Clears a previous incident - called whenever a walk succeeds, so a resumed instance doesn't keep a stale one. */
    public void clearIncident() {
        this.incidentNodeId = null;
        this.incidentMessage = null;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
