package com.example.bpmn.controller;

import com.example.bpmn.http.BaseController;
import com.example.bpmn.service.ProcessInstanceService;

/**
 * Operate-style actions that don't belong under {@code /api/process-instances} itself.
 * Currently just incident retry; the Angular Operate screen's other calls
 * (metrics, per-instance variables/audit-trail, cancel) are out of scope for now and
 * degrade gracefully client-side when they 404.
 */
public class OperateController extends BaseController {

    public OperateController(ProcessInstanceService processInstanceService) {
        post("/api/operate/incidents/:id/retry", ctx ->
                processInstanceService.retryIncident(ctx.param("id"), ctx.authUsername()));
    }
}
