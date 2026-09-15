package com.example.bpmn.service;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.dto.StartProcessInstanceRequest;

public interface ProcessInstanceService {
    ProcessInstanceResponse startInstance(StartProcessInstanceRequest request, String requesterUsername);
    ProcessInstanceResponse getInstanceById(String id);
    PageResponse<ProcessInstanceResponse> listInstances(int page, int size);
}
