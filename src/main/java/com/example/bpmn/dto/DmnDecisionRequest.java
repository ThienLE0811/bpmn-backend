package com.example.bpmn.dto;

public class DmnDecisionRequest {
    private String decisionKey;
    private String name;
    private String description;
    private String hitPolicy;
    private String category;
    private String dmnXml;

    public DmnDecisionRequest() {
    }

    public DmnDecisionRequest(String decisionKey, String name, String description, String hitPolicy, String category, String dmnXml) {
        this.decisionKey = decisionKey;
        this.name = name;
        this.description = description;
        this.hitPolicy = hitPolicy;
        this.category = category;
        this.dmnXml = dmnXml;
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

    public String getDmnXml() {
        return dmnXml;
    }

    public void setDmnXml(String dmnXml) {
        this.dmnXml = dmnXml;
    }
}
