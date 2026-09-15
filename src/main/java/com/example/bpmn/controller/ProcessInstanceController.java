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

        get("/api/process-instances", ctx -> processInstanceService.listInstances(ctx.pageParam(), ctx.sizeParam()));
        post("/api/process-instances", ctx -> HttpResult.created(
                processInstanceService.startInstance(ctx.body(StartProcessInstanceRequest.class), ctx.authUsername())));
        get("/api/process-instances/:id", ctx -> processInstanceService.getInstanceById(ctx.param("id")));
    }
}
