package com.example.bpmn.repository;

import com.example.bpmn.model.ProcessInstance;
import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRepository {
    ProcessInstance save(ProcessInstance instance);
    Optional<ProcessInstance> findById(String id);
    List<ProcessInstance> findAll();
    List<ProcessInstance> findPage(int limit, int offset);
    long count();
    boolean deleteById(String id);
}
