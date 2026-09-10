package com.example.bpmn.mapper;

import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.model.DmnDecision;

public final class DmnDecisionMapper {

    private DmnDecisionMapper() {
    }

    public static DmnDecisionResponse toResponse(DmnDecision decision) {
        if (decision == null) {
            return null;
        }
        return new DmnDecisionResponse(
                decision.getId(),
                decision.getDecisionKey(),
                decision.getName(),
                decision.getDescription(),
                decision.getHitPolicy(),
                decision.getCategory(),
                decision.getVersion(),
                decision.getDmnXml(),
                decision.getStatus(),
                decision.getCreatedBy(),
                decision.getUpdatedBy(),
                decision.getCreatedAt(),
                decision.getUpdatedAt()
        );
    }
}
