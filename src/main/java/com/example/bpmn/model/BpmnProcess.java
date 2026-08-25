package com.example.bpmn.model;

import java.time.LocalDateTime;

public class BpmnProcess {
    private String id;
    private String processKey;
    private String name;
    private Integer version;
    private String bpmnXml;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BpmnProcess() {
    }

    public BpmnProcess(String id, String processKey, String name, Integer version, String bpmnXml, String status) {
        this.id = id;
        this.processKey = processKey;
        this.name = name;
        this.version = version;
        this.bpmnXml = bpmnXml;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProcessKey() {
        return processKey;
    }

    public void setProcessKey(String processKey) {
        this.processKey = processKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
