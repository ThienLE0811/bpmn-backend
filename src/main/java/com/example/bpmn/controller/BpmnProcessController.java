package com.example.bpmn.controller;

import com.example.bpmn.dto.BpmnProcessRequest;
import com.example.bpmn.dto.BpmnProcessUpdateRequest;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.http.HttpResult;
import com.example.bpmn.service.BpmnProcessService;

import java.util.Map;

/**
 * Controller handling REST API requests for BPMN Process resources.
 */
public class BpmnProcessController extends BaseController {
    private final BpmnProcessService bpmnProcessService;

    public BpmnProcessController(BpmnProcessService bpmnProcessService) {
        this.bpmnProcessService = bpmnProcessService;

        get("/api/bpmn-processes", ctx -> bpmnProcessService.getAllProcesses());
        post("/api/bpmn-processes", ctx -> HttpResult.created(
                bpmnProcessService.createProcess(ctx.body(BpmnProcessRequest.class), ctx.authUsername())));
        get("/api/bpmn-processes/key/:key", ctx -> bpmnProcessService.getProcessByKey(ctx.param("key")));
        get("/api/bpmn-processes/:id", ctx -> bpmnProcessService.getProcessById(ctx.param("id")));
        put("/api/bpmn-processes/:id", ctx -> bpmnProcessService.updateProcess(
                ctx.param("id"), ctx.body(BpmnProcessUpdateRequest.class), ctx.authUsername(), ctx.authRole()));
        get("/api/bpmn-processes/:id/versions", ctx -> bpmnProcessService.getVersionHistory(ctx.param("id")));
        get("/api/bpmn-processes/:id/versions/:version", ctx -> {
            String versionParam = ctx.param("version");
            try {
                return bpmnProcessService.getVersion(ctx.param("id"), Integer.parseInt(versionParam));
            } catch (NumberFormatException e) {
                throw new AppException("Invalid version number: " + versionParam, 400);
            }
        });
        delete("/api/bpmn-processes/:id", ctx -> {
            String id = ctx.param("id");
            bpmnProcessService.deleteProcess(id, ctx.authUsername(), ctx.authRole());
            return Map.of("message", "BPMN process deleted successfully", "id", id);
        });
    }
}
