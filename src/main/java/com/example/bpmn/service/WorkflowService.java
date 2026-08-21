package com.example.bpmn.service;

import com.example.bpmn.dto.WorkflowRequest;
import com.example.bpmn.dto.WorkflowResponse;

import java.util.List;

public interface WorkflowService {
    WorkflowResponse createWorkflow(WorkflowRequest request);
    WorkflowResponse getWorkflowById(String id);
    List<WorkflowResponse> getAllWorkflows();
    void deleteWorkflow(String id);
}
