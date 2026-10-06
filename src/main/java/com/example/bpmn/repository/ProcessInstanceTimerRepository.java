package com.example.bpmn.repository;

import com.example.bpmn.model.ProcessInstanceTimer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProcessInstanceTimerRepository {
    ProcessInstanceTimer save(ProcessInstanceTimer timer);
    Optional<ProcessInstanceTimer> findById(String id);
    List<ProcessInstanceTimer> findByProcessInstanceId(String processInstanceId);
    /** Timer waits whose {@code dueDate} has already passed. */
    List<ProcessInstanceTimer> findDueTimers(LocalDateTime now);
    boolean deleteById(String id);
}
