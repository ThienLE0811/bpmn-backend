package com.example.bpmn.service;

import com.example.bpmn.dto.BpmnProcessRequest;
import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.dto.BpmnProcessUpdateRequest;
import com.example.bpmn.dto.BpmnProcessVersionResponse;

import java.util.List;

public interface BpmnProcessService {
    List<BpmnProcessResponse> getAllProcesses();
    BpmnProcessResponse getProcessById(String id);
    BpmnProcessResponse getProcessByKey(String processKey);
    BpmnProcessResponse createProcess(BpmnProcessRequest request);
    BpmnProcessResponse updateProcess(String id, BpmnProcessUpdateRequest request);
    void deleteProcess(String id);
    List<BpmnProcessVersionResponse> getVersionHistory(String processId);
    BpmnProcessVersionResponse getVersion(String processId, int version);
}
