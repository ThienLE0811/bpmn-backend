package com.example.bpmn.service;

import com.example.bpmn.dto.BpmnProcessResponse;

import java.util.List;

public interface BpmnProcessService {
    List<BpmnProcessResponse> getAllProcesses();
    BpmnProcessResponse getProcessById(String id);
    BpmnProcessResponse getProcessByKey(String processKey);
}
