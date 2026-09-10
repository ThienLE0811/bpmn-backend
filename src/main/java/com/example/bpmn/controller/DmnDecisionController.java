package com.example.bpmn.controller;

import com.example.bpmn.http.BaseController;
import com.example.bpmn.service.DmnDecisionService;

/**
 * Controller handling REST API requests for DMN Decision resources.
 */
public class DmnDecisionController extends BaseController {
    private final DmnDecisionService dmnDecisionService;

    public DmnDecisionController(DmnDecisionService dmnDecisionService) {
        this.dmnDecisionService = dmnDecisionService;

        get("/api/dmn-decisions", ctx -> dmnDecisionService.getAllDecisions());
        get("/api/dmn-decisions/key/:key", ctx -> dmnDecisionService.getDecisionByKey(ctx.param("key")));
        get("/api/dmn-decisions/:id", ctx -> dmnDecisionService.getDecisionById(ctx.param("id")));
    }
}
