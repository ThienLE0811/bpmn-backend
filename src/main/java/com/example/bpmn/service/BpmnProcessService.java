package com.example.bpmn.service;

import com.example.bpmn.dto.BpmnProcessRequest;
import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.dto.BpmnProcessUpdateRequest;
import com.example.bpmn.dto.BpmnProcessVersionResponse;
import com.example.bpmn.dto.PageResponse;

import java.util.List;

public interface BpmnProcessService {
    PageResponse<BpmnProcessResponse> getAllProcesses(int page, int size);
    BpmnProcessResponse getProcessById(String id);
    BpmnProcessResponse getProcessByKey(String processKey);
    BpmnProcessResponse createProcess(BpmnProcessRequest request, String requesterUsername);
    BpmnProcessResponse updateProcess(String id, BpmnProcessUpdateRequest request, String requesterUsername, String requesterRole);
    void deleteProcess(String id, String requesterUsername, String requesterRole);
    List<BpmnProcessVersionResponse> getVersionHistory(String processId);
    BpmnProcessVersionResponse getVersion(String processId, int version);
}
