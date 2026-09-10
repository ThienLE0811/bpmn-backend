package com.example.bpmn.mapper;

import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.model.BpmnProcess;

public final class BpmnProcessMapper {

    private BpmnProcessMapper() {
    }

    public static BpmnProcessResponse toResponse(BpmnProcess process) {
        if (process == null) {
            return null;
        }
        return new BpmnProcessResponse(
                process.getId(),
                process.getProcessKey(),
                process.getName(),
                process.getDescription(),
                process.getCategory(),
                process.getVersion(),
                process.getBpmnXml(),
                process.getStatus(),
                process.getCreatedBy(),
                process.getUpdatedBy(),
                process.getCreatedAt(),
                process.getUpdatedAt()
        );
    }
}
