package com.example.bpmn.repository;

import com.example.bpmn.model.DmnDecisionVersion;
import java.util.List;
import java.util.Optional;

public interface DmnDecisionVersionRepository {
    DmnDecisionVersion save(DmnDecisionVersion version);
    List<DmnDecisionVersion> findByDecisionId(String decisionId);
    Optional<DmnDecisionVersion> findByDecisionIdAndVersion(String decisionId, int version);
}
