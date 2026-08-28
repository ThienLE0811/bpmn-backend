package com.example.bpmn.repository;

import com.example.bpmn.model.DmnDecision;
import java.util.List;
import java.util.Optional;

public interface DmnDecisionRepository {
    DmnDecision save(DmnDecision decision);
    Optional<DmnDecision> findById(String id);
    Optional<DmnDecision> findByDecisionKey(String decisionKey);
    List<DmnDecision> findAll();
    boolean deleteById(String id);
}
