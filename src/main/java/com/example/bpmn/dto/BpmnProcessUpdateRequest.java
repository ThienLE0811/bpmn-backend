package com.example.bpmn.dto;

/**
 * Fields not sent (null) are left unchanged on the existing record.
 */
public class BpmnProcessUpdateRequest {
    private String name;
    private String description;
    private String category;
    private String bpmnXml;
    private String status;

    public BpmnProcessUpdateRequest() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
