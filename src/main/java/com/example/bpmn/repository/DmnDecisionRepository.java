package com.example.bpmn.repository;

import com.example.bpmn.model.DmnDecision;
import java.util.List;
import java.util.Optional;

public interface DmnDecisionRepository {
    DmnDecision save(DmnDecision decision);
    Optional<DmnDecision> findById(String id);
    Optional<DmnDecision> findByDecisionKey(String decisionKey);
    List<DmnDecision> findAll();
    List<DmnDecision> findPage(int limit, int offset);
    long count();
    boolean deleteById(String id);
}
