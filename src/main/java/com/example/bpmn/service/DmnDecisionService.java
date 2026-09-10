package com.example.bpmn.service;

import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;

import java.util.List;

public interface DmnDecisionService {
    List<DmnDecisionResponse> getAllDecisions();
    DmnDecisionResponse getDecisionById(String id);
    DmnDecisionResponse getDecisionByKey(String decisionKey);
    DmnDecisionResponse createDecision(DmnDecisionRequest request);
    DmnDecisionResponse updateDecision(String id, DmnDecisionUpdateRequest request);
    void deleteDecision(String id);
}
