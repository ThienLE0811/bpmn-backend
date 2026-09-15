package com.example.bpmn.mapper;

import com.example.bpmn.dto.TaskResponse;
import com.example.bpmn.model.Task;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskResponse(
                task.getId(),
                task.getProcessInstanceId(),
                task.getNodeId(),
                task.getName(),
                task.getDescription(),
                task.getStatus(),
                task.getAssigneeId(),
                task.getClaimedBy(),
                task.getClaimedAt(),
                task.getCompletedBy(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
