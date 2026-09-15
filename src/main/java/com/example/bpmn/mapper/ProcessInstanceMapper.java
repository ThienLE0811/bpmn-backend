package com.example.bpmn.mapper;

import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.model.ProcessInstance;

public final class ProcessInstanceMapper {

    private ProcessInstanceMapper() {
    }

    public static ProcessInstanceResponse toResponse(ProcessInstance instance) {
        if (instance == null) {
            return null;
        }
        return new ProcessInstanceResponse(
                instance.getId(),
                instance.getProcessId(),
                instance.getProcessVersion(),
                instance.getStatus(),
                instance.getCurrentNodeId(),
                instance.getVariables(),
                instance.getStartedBy(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                instance.getCreatedAt(),
                instance.getUpdatedAt()
        );
    }
}
