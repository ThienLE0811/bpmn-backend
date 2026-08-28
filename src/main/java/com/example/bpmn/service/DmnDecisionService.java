package com.example.bpmn.service;

import com.example.bpmn.dto.DmnDecisionResponse;

import java.util.List;

public interface DmnDecisionService {
    List<DmnDecisionResponse> getAllDecisions();
    DmnDecisionResponse getDecisionById(String id);
    DmnDecisionResponse getDecisionByKey(String decisionKey);
}
