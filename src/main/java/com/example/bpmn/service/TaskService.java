package com.example.bpmn.service;

import com.example.bpmn.dto.CompleteTaskRequest;
import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.TaskResponse;

public interface TaskService {
    PageResponse<TaskResponse> listTasks(String statusFilter, boolean onlyMine, String requesterUserId, int page, int size);
    TaskResponse getTaskById(String id);
    TaskResponse claimTask(String id, String requesterUserId);
    TaskResponse completeTask(String id, String requesterUserId, String requesterRole, CompleteTaskRequest request);
    /** Cancels every open task whose boundary-timer deadline has passed and advances its process instance past the boundary event. Intended to be called periodically by a scheduler. */
    void processDueTimers();
}
