package com.example.bpmn.controller;

import com.example.bpmn.dto.StartProcessInstanceRequest;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.http.HttpResult;
import com.example.bpmn.service.ProcessInstanceService;

/**
 * Controller handling REST API requests for running process instances ("cases").
 */
public class ProcessInstanceController extends BaseController {
    private final ProcessInstanceService processInstanceService;

    public ProcessInstanceController(ProcessInstanceService processInstanceService) {
        this.processInstanceService = processInstanceService;

        get("/api/process-instances", ctx -> processInstanceService.listInstances(
                ctx.pageParam(), ctx.sizeParam(), ctx.query("status"), ctx.query("search")));
        post("/api/process-instances", ctx -> HttpResult.created(
                processInstanceService.startInstance(ctx.body(StartProcessInstanceRequest.class), ctx.authUsername())));
        // Registered before the catch-all ":id" pattern below, per BaseController's matching-order rule.
        get("/api/process-instances/:id/incidents", ctx -> processInstanceService.getIncidents(ctx.param("id")));
        get("/api/process-instances/:id", ctx -> processInstanceService.getInstanceById(ctx.param("id")));
    }
}
