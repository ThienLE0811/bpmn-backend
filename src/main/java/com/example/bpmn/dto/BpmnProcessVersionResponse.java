package com.example.bpmn.dto;

import java.time.LocalDateTime;

public class BpmnProcessVersionResponse {
    private String id;
    private String processId;
    private Integer version;
    private String bpmnXml;
    private String createdBy;
    private LocalDateTime createdAt;

    public BpmnProcessVersionResponse() {
    }

    public BpmnProcessVersionResponse(String id, String processId, Integer version, String bpmnXml,
                                       String createdBy, LocalDateTime createdAt) {
        this.id = id;
        this.processId = processId;
        this.version = version;
        this.bpmnXml = bpmnXml;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getBpmnXml() {
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml) {
        this.bpmnXml = bpmnXml;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
