package com.example.bpmn.model;

import java.time.LocalDateTime;

public class DmnDecision {
    private String id;
    private String decisionKey;
    private String name;
    private String description;
    private String hitPolicy;
    private String category;
    private Integer version;
    private String dmnXml;
    private String status;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DmnDecision() {
    }

    public DmnDecision(String id, String decisionKey, String name, Integer version, String dmnXml, String status) {
        this.id = id;
        this.decisionKey = decisionKey;
        this.name = name;
        this.version = version;
        this.dmnXml = dmnXml;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public DmnDecision(String id, String decisionKey, String name, String description, String hitPolicy,
                       String category, Integer version, String dmnXml, String status,
                       String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.decisionKey = decisionKey;
        this.name = name;
        this.description = description;
        this.hitPolicy = hitPolicy;
        this.category = category;
        this.version = version;
        this.dmnXml = dmnXml;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDecisionKey() {
        return decisionKey;
    }

    public void setDecisionKey(String decisionKey) {
        this.decisionKey = decisionKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHitPolicy() {
        return hitPolicy;
    }

    public void setHitPolicy(String hitPolicy) {
        this.hitPolicy = hitPolicy;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getDmnXml() {
        return dmnXml;
    }

    public void setDmnXml(String dmnXml) {
        this.dmnXml = dmnXml;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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
