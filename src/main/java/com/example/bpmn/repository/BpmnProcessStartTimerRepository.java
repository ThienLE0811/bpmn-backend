package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnProcessStartTimer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BpmnProcessStartTimerRepository {
    /** Upserts by {@code processId} - at most one schedule row per process. */
    BpmnProcessStartTimer save(BpmnProcessStartTimer timer);
    Optional<BpmnProcessStartTimer> findByProcessId(String processId);
    /** Schedules whose {@code nextFireAt} has already passed. */
    List<BpmnProcessStartTimer> findDue(LocalDateTime now);
    void deleteByProcessId(String processId);
}
