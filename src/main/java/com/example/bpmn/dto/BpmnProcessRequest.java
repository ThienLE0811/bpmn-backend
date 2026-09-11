package com.example.bpmn.dto;

public class BpmnProcessRequest {
    private String processKey;
    private String name;
    private String description;
    private String category;
    private String bpmnXml;

    public BpmnProcessRequest() {
    }

    public BpmnProcessRequest(String processKey, String name, String description, String category, String bpmnXml) {
        this.processKey = processKey;
        this.name = name;
        this.description = description;
        this.category = category;
        this.bpmnXml = bpmnXml;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBpmnXml() {
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml) {
        this.bpmnXml = bpmnXml;
    }
}
