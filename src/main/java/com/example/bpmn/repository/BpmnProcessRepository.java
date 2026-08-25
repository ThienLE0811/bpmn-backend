package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnProcess;
import java.util.List;
import java.util.Optional;

public interface BpmnProcessRepository {
    BpmnProcess save(BpmnProcess process);
    Optional<BpmnProcess> findById(String id);
    Optional<BpmnProcess> findByProcessKey(String processKey);
    List<BpmnProcess> findAll();
    boolean deleteById(String id);
}
