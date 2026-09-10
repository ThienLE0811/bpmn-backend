package com.example.bpmn.controller;

import com.example.bpmn.dto.WorkflowRequest;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.http.HttpResult;
import com.example.bpmn.service.WorkflowService;

import java.util.Map;

/**
 * Controller handling REST API requests for Workflow resources.
 */
public class WorkflowController extends BaseController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;

        get("/api/workflows", ctx -> workflowService.getAllWorkflows());
        post("/api/workflows", ctx -> HttpResult.created(
                workflowService.createWorkflow(ctx.body(WorkflowRequest.class))));
        get("/api/workflows/:id", ctx -> workflowService.getWorkflowById(ctx.param("id")));
        delete("/api/workflows/:id", ctx -> {
            String id = ctx.param("id");
            workflowService.deleteWorkflow(id);
            return Map.of("message", "Workflow deleted successfully", "id", id);
        });
    }
}
