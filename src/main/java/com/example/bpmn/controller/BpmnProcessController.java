package com.example.bpmn.controller;

import com.example.bpmn.http.BaseController;
import com.example.bpmn.service.BpmnProcessService;

/**
 * Controller handling REST API requests for BPMN Process resources.
 */
public class BpmnProcessController extends BaseController {
    private final BpmnProcessService bpmnProcessService;

    public BpmnProcessController(BpmnProcessService bpmnProcessService) {
        this.bpmnProcessService = bpmnProcessService;

        get("/api/bpmn-processes", ctx -> bpmnProcessService.getAllProcesses());
        get("/api/bpmn-processes/key/:key", ctx -> bpmnProcessService.getProcessByKey(ctx.param("key")));
        get("/api/bpmn-processes/:id", ctx -> bpmnProcessService.getProcessById(ctx.param("id")));
    }
}
