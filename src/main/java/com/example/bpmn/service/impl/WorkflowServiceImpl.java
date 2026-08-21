package com.example.bpmn.service.impl;

import com.example.bpmn.dto.WorkflowRequest;
import com.example.bpmn.dto.WorkflowResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.Workflow;
import com.example.bpmn.repository.WorkflowRepository;
import com.example.bpmn.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class WorkflowServiceImpl implements WorkflowService {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowServiceImpl.class);
    private final WorkflowRepository workflowRepository;

    public WorkflowServiceImpl(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Override
    public WorkflowResponse createWorkflow(WorkflowRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new AppException("Workflow name must not be empty", 400);
        }

        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        Workflow workflow = new Workflow(
                id,
                request.getName(),
                request.getDescription(),
                "CREATED",
                now,
                now
        );

        Workflow saved = workflowRepository.save(workflow);
        logger.info("Saved new workflow with ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public WorkflowResponse getWorkflowById(String id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new AppException("Workflow not found with id: " + id, 404));
        return mapToResponse(workflow);
    }

    @Override
    public List<WorkflowResponse> getAllWorkflows() {
        return workflowRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteWorkflow(String id) {
        boolean deleted = workflowRepository.deleteById(id);
        if (!deleted) {
            throw new AppException("Workflow not found with id: " + id, 404);
        }
        logger.info("Deleted workflow with ID: {}", id);
    }

    private WorkflowResponse mapToResponse(Workflow workflow) {
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getStatus(),
                workflow.getCreatedAt()
        );
    }
}
