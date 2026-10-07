package com.example.bpmn.service;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.ProcessIncidentResponse;
import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.dto.StartProcessInstanceRequest;

import java.util.List;

public interface ProcessInstanceService {
    ProcessInstanceResponse startInstance(StartProcessInstanceRequest request, String requesterUsername);
    ProcessInstanceResponse getInstanceById(String id);

    default PageResponse<ProcessInstanceResponse> listInstances(int page, int size) {
        return listInstances(page, size, null, null);
    }

    /** @param status exact match (case-insensitive) against the instance status, or null/blank for any.
     *  @param search case-insensitive substring match against id, processId or startedBy, or null/blank for any. */
    PageResponse<ProcessInstanceResponse> listInstances(int page, int size, String status, String search);

    /** Auto-starts a new instance for every BPMN process whose timer start event is due. Intended to be called periodically by a scheduler. */
    void processDueStartTimers();

    /** The instance's current incident as a 0-1 element list (empty unless its status is FAILED), matching the Operate UI's incidents-per-instance contract. */
    List<ProcessIncidentResponse> getIncidents(String instanceId);

    /** Re-walks a FAILED instance from its incident node with its current variables - the connector that failed runs again. */
    ProcessInstanceResponse retryIncident(String instanceId, String requesterUsername);
}
