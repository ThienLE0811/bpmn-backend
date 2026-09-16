package com.example.bpmn.service.impl;

import com.example.bpmn.dto.CompleteTaskRequest;
import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.TaskResponse;
import com.example.bpmn.engine.AdvanceResult;
import com.example.bpmn.engine.BpmnGraphParser;
import com.example.bpmn.engine.BpmnNode;
import com.example.bpmn.engine.BpmnProcessDefinition;
import com.example.bpmn.engine.ProcessEngine;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.TaskMapper;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TaskServiceImpl implements TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);
    private final TaskRepository taskRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BpmnProcessVersionRepository bpmnProcessVersionRepository;
    private final DmnDecisionService dmnDecisionService;

    public TaskServiceImpl(TaskRepository taskRepository,
                            ProcessInstanceRepository processInstanceRepository,
                            BpmnProcessVersionRepository bpmnProcessVersionRepository,
                            DmnDecisionService dmnDecisionService) {
        this.taskRepository = taskRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.bpmnProcessVersionRepository = bpmnProcessVersionRepository;
        this.dmnDecisionService = dmnDecisionService;
    }

    @Override
    public PageResponse<TaskResponse> listTasks(String statusFilter, boolean onlyMine, String requesterUserId, int page, int size) {
        List<Task> filtered = taskRepository.findAll().stream()
                .filter(task -> statusFilter == null || statusFilter.equalsIgnoreCase(task.getStatus()))
                .filter(task -> !onlyMine || requesterUserId.equals(task.getClaimedBy()))
                .collect(Collectors.toList());

        int fromIndex = Math.min((page - 1) * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<TaskResponse> content = filtered.subList(fromIndex, toIndex).stream()
                .map(TaskMapper::toResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(content, page, size, filtered.size());
    }

    @Override
    public TaskResponse getTaskById(String id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException("Task not found with id: " + id, 404));
        return TaskMapper.toResponse(task);
    }

    @Override
    public TaskResponse claimTask(String id, String requesterUserId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException("Task not found with id: " + id, 404));

        if (!"PENDING".equals(task.getStatus())) {
            throw new AppException("Task is not available to claim (status: " + task.getStatus() + ")", 409);
        }

        LocalDateTime now = LocalDateTime.now();
        task.setStatus("CLAIMED");
        task.setClaimedBy(requesterUserId);
        task.setClaimedAt(now);
        task.setUpdatedAt(now);

        Task saved = taskRepository.save(task);
        logger.info("Task {} claimed by {}", saved.getId(), requesterUserId);
        return TaskMapper.toResponse(saved);
    }

    @Override
    public TaskResponse completeTask(String id, String requesterUserId, String requesterRole, CompleteTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException("Task not found with id: " + id, 404));

        if (!"CLAIMED".equals(task.getStatus())) {
            throw new AppException("Task must be claimed before it can be completed (status: " + task.getStatus() + ")", 409);
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        if (!isAdmin && !requesterUserId.equals(task.getClaimedBy())) {
            throw new AppException("Only the user who claimed this task or an administrator can complete it", 403);
        }

        ProcessInstance instance = processInstanceRepository.findById(task.getProcessInstanceId())
                .orElseThrow(() -> new AppException("Process instance not found with id: " + task.getProcessInstanceId(), 404));

        Map<String, Object> variables = new HashMap<>(instance.getVariables() != null ? instance.getVariables() : Map.of());
        if (request != null && request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }

        String bpmnXml = bpmnProcessVersionRepository.findByProcessIdAndVersion(instance.getProcessId(), instance.getProcessVersion())
                .map(version -> version.getBpmnXml())
                .orElseThrow(() -> new AppException("BPMN version snapshot not found for process "
                        + instance.getProcessId() + " v" + instance.getProcessVersion(), 500));
        BpmnProcessDefinition definition = BpmnGraphParser.parse(bpmnXml);

        Set<String> pendingJoinArrivals = instance.getPendingJoinArrivals() != null
                ? instance.getPendingJoinArrivals() : Set.of();
        // Other branches still open elsewhere in this instance - an inclusive join needs these
        // to tell which of its incoming flows are still expected to arrive.
        Set<String> otherActiveNodeIds = taskRepository.findByProcessInstanceId(instance.getId()).stream()
                .filter(t -> !t.getId().equals(task.getId()))
                .filter(t -> "PENDING".equals(t.getStatus()) || "CLAIMED".equals(t.getStatus()))
                .map(Task::getNodeId)
                .collect(Collectors.toSet());
        AdvanceResult result = ProcessEngine.advance(definition, task.getNodeId(), variables, pendingJoinArrivals,
                otherActiveNodeIds, dmnDecisionService::evaluate);
        instance.setVariables(result.getUpdatedVariables());

        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED");
        task.setCompletedBy(requesterUserId);
        task.setCompletedAt(now);
        task.setUpdatedAt(now);
        Task savedTask = taskRepository.save(task);

        for (String nodeId : result.getNewUserTaskNodeIds()) {
            createTaskForNode(instance, definition, nodeId);
        }
        instance.setPendingJoinArrivals(result.getPendingJoinArrivals());

        // A parallel fork can leave other sibling tasks (created in this same instance by an
        // earlier or later branch) still open, so instance completion depends on all of them,
        // not just the branch this call just walked.
        List<Task> openTasks = taskRepository.findByProcessInstanceId(instance.getId()).stream()
                .filter(t -> "PENDING".equals(t.getStatus()) || "CLAIMED".equals(t.getStatus()))
                .collect(Collectors.toList());

        if (openTasks.isEmpty() && result.getPendingJoinArrivals().isEmpty()) {
            instance.setStatus("COMPLETED");
            instance.setCurrentNodeId(null);
            instance.setCompletedAt(now);
        } else {
            instance.setStatus("RUNNING");
            instance.setCurrentNodeId(openTasks.stream().map(Task::getNodeId).collect(Collectors.joining(",")));
        }
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);

        logger.info("Task {} completed by {}, process instance {} now {}",
                savedTask.getId(), requesterUserId, instance.getId(), instance.getStatus());
        return TaskMapper.toResponse(savedTask);
    }

    private void createTaskForNode(ProcessInstance instance, BpmnProcessDefinition definition, String nodeId) {
        BpmnNode node = definition.getNode(nodeId);
        LocalDateTime now = LocalDateTime.now();

        Task next = new Task();
        next.setId(UUID.randomUUID().toString());
        next.setProcessInstanceId(instance.getId());
        next.setNodeId(nodeId);
        next.setName(node != null && node.getName() != null ? node.getName() : nodeId);
        next.setStatus("PENDING");
        next.setCreatedAt(now);
        next.setUpdatedAt(now);
        taskRepository.save(next);
    }
}
