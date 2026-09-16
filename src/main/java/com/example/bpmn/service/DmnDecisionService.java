package com.example.bpmn.service;

import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;
import com.example.bpmn.dto.PageResponse;

import java.util.Map;

public interface DmnDecisionService {
    PageResponse<DmnDecisionResponse> getAllDecisions(int page, int size);
    DmnDecisionResponse getDecisionById(String id);
    DmnDecisionResponse getDecisionByKey(String decisionKey);
    DmnDecisionResponse createDecision(DmnDecisionRequest request, String requesterUsername);
    DmnDecisionResponse updateDecision(String id, DmnDecisionUpdateRequest request, String requesterUsername, String requesterRole);
    void deleteDecision(String id, String requesterUsername, String requesterRole);

    /** Evaluates the decision table identified by {@code decisionKey} against {@code variables} and returns its output columns. Used by {@link com.example.bpmn.engine.ProcessEngine} for business rule tasks. */
    Map<String, Object> evaluate(String decisionKey, Map<String, Object> variables);
}
