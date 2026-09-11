package com.example.bpmn.service;

import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;

import java.util.List;

public interface DmnDecisionService {
    List<DmnDecisionResponse> getAllDecisions();
    DmnDecisionResponse getDecisionById(String id);
    DmnDecisionResponse getDecisionByKey(String decisionKey);
    DmnDecisionResponse createDecision(DmnDecisionRequest request, String requesterUsername);
    DmnDecisionResponse updateDecision(String id, DmnDecisionUpdateRequest request, String requesterUsername, String requesterRole);
    void deleteDecision(String id, String requesterUsername, String requesterRole);
}
