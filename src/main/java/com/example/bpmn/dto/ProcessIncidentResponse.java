package com.example.bpmn.dto;

import java.time.LocalDateTime;

/**
 * The Operate UI's per-instance incident shape. Synthesized from {@link com.example.bpmn.model.ProcessInstance}'s
 * single incident column pair rather than backed by its own table - {@code id} is the process
 * instance id, since phase 1 records at most one open incident per instance.
 */
public class ProcessIncidentResponse {
    private String id;
    private String processInstanceId;
    private String activityId;
    private String activityName;
    private String errorType;
    private String errorMessage;
    private LocalDateTime creationTime;
    private String state;

    public ProcessIncidentResponse() {
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

    /** Node id the instance is stuck at. */
    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    /** The node's display name, falling back to {@link #getActivityId()} when it has none or the BPMN version can't be resolved. */
    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(LocalDateTime creationTime) {
        this.creationTime = creationTime;
    }

    /** Always "OPEN" today - retrying either clears the incident (it stops being returned) or replaces it with a new one. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
