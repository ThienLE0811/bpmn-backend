package com.example.bpmn.service;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.WorkflowRequest;
import com.example.bpmn.dto.WorkflowResponse;

public interface WorkflowService {
    WorkflowResponse createWorkflow(WorkflowRequest request);
    WorkflowResponse getWorkflowById(String id);
    PageResponse<WorkflowResponse> getAllWorkflows(int page, int size);
    void deleteWorkflow(String id, String requesterRole);
}
