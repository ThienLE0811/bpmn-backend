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
        ProcessInstanceResponse response = new ProcessInstanceResponse(
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
        // Set apart from the constructor, which is already long enough - these two are null on
        // every instance that hasn't failed.
        response.setIncidentNodeId(instance.getIncidentNodeId());
        response.setIncidentMessage(instance.getIncidentMessage());
        return response;
    }
}
