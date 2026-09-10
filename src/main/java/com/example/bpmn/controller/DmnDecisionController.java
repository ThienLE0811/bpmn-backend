package com.example.bpmn.controller;

import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;
import com.example.bpmn.http.BaseController;
import com.example.bpmn.http.HttpResult;
import com.example.bpmn.service.DmnDecisionService;

import java.util.Map;

/**
 * Controller handling REST API requests for DMN Decision resources.
 */
public class DmnDecisionController extends BaseController {
    private final DmnDecisionService dmnDecisionService;

    public DmnDecisionController(DmnDecisionService dmnDecisionService) {
        this.dmnDecisionService = dmnDecisionService;

        get("/api/dmn-decisions", ctx -> dmnDecisionService.getAllDecisions());
        post("/api/dmn-decisions", ctx -> HttpResult.created(
                dmnDecisionService.createDecision(ctx.body(DmnDecisionRequest.class))));
        get("/api/dmn-decisions/key/:key", ctx -> dmnDecisionService.getDecisionByKey(ctx.param("key")));
        get("/api/dmn-decisions/:id", ctx -> dmnDecisionService.getDecisionById(ctx.param("id")));
        put("/api/dmn-decisions/:id", ctx -> dmnDecisionService.updateDecision(
                ctx.param("id"), ctx.body(DmnDecisionUpdateRequest.class)));
        delete("/api/dmn-decisions/:id", ctx -> {
            String id = ctx.param("id");
            dmnDecisionService.deleteDecision(id);
            return Map.of("message", "DMN decision deleted successfully", "id", id);
        });
    }
}
