package com.example.bpmn.mapper;

import com.example.bpmn.dto.BpmnProcessVersionResponse;
import com.example.bpmn.model.BpmnProcessVersion;

public final class BpmnProcessVersionMapper {

    private BpmnProcessVersionMapper() {
    }

    public static BpmnProcessVersionResponse toResponse(BpmnProcessVersion version) {
        if (version == null) {
            return null;
        }
        return new BpmnProcessVersionResponse(
                version.getId(),
                version.getProcessId(),
                version.getVersion(),
                version.getBpmnXml(),
                version.getCreatedBy(),
                version.getCreatedAt()
        );
    }
}
