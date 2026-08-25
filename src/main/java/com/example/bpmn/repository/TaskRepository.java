package com.example.bpmn.repository;

import com.example.bpmn.model.Task;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    Task save(Task task);
    Optional<Task> findById(String id);
    List<Task> findByProcessId(String processId);
    List<Task> findByAssigneeId(String assigneeId);
    List<Task> findAll();
    boolean deleteById(String id);
}
