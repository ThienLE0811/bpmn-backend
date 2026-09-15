package com.example.bpmn.repository.impl;

import com.example.bpmn.model.Workflow;
import com.example.bpmn.repository.WorkflowRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkflowRepository implements WorkflowRepository {
    private final Map<String, Workflow> storage = new ConcurrentHashMap<>();

    @Override
    public Workflow save(Workflow workflow) {
        storage.put(workflow.getId(), workflow);
        return workflow;
    }

    @Override
    public Optional<Workflow> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Workflow> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Workflow> findPage(int limit, int offset) {
        List<Workflow> all = findAll();
        int fromIndex = Math.min(offset, all.size());
        int toIndex = Math.min(offset + limit, all.size());
        return new ArrayList<>(all.subList(fromIndex, toIndex));
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public boolean deleteById(String id) {
        return storage.remove(id) != null;
    }
}
