package com.example.bpmn.mapper;

import com.example.bpmn.dto.WorkflowResponse;
import com.example.bpmn.model.Workflow;

public final class WorkflowMapper {

    private WorkflowMapper() {
    }

    public static WorkflowResponse toResponse(Workflow workflow) {
        if (workflow == null) {
            return null;
        }
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getStatus(),
                workflow.getCreatedAt()
        );
    }
}
