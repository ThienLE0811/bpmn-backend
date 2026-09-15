package com.example.bpmn.repository;

import com.example.bpmn.model.Workflow;
import java.util.List;
import java.util.Optional;

public interface WorkflowRepository {
    Workflow save(Workflow workflow);
    Optional<Workflow> findById(String id);
    List<Workflow> findAll();
    List<Workflow> findPage(int limit, int offset);
    long count();
    boolean deleteById(String id);
}
