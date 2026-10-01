package com.example.bpmn.repository;

import com.example.bpmn.model.Task;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    Task save(Task task);
    Optional<Task> findById(String id);
    List<Task> findByProcessId(String processId);
    List<Task> findByAssigneeId(String assigneeId);
    List<Task> findByProcessInstanceId(String processInstanceId);
    List<Task> findAll();
    boolean deleteById(String id);
    /** Open (PENDING/CLAIMED) tasks whose boundary-timer {@code dueDate} has already passed. */
    List<Task> findDueTimers(LocalDateTime now);
}
