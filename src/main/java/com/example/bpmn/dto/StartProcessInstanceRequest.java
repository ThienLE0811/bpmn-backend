package com.example.bpmn.dto;

import java.util.Map;

public class StartProcessInstanceRequest {
    private String processId;
    private Map<String, Object> variables;

    public StartProcessInstanceRequest() {
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
