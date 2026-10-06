package com.example.bpmn.service;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.dto.StartProcessInstanceRequest;

public interface ProcessInstanceService {
    ProcessInstanceResponse startInstance(StartProcessInstanceRequest request, String requesterUsername);
    ProcessInstanceResponse getInstanceById(String id);
    PageResponse<ProcessInstanceResponse> listInstances(int page, int size);
    /** Auto-starts a new instance for every BPMN process whose timer start event is due. Intended to be called periodically by a scheduler. */
    void processDueStartTimers();
}
