package com.example.bpmn.service.impl;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.dto.StartProcessInstanceRequest;
import com.example.bpmn.engine.AdvanceResult;
import com.example.bpmn.engine.BpmnGraphParser;
import com.example.bpmn.engine.BpmnNode;
import com.example.bpmn.engine.BpmnProcessDefinition;
import com.example.bpmn.engine.ProcessEngine;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.ProcessInstanceMapper;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.service.ProcessInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProcessInstanceServiceImpl implements ProcessInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceServiceImpl.class);
    private final BpmnProcessRepository bpmnProcessRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskRepository taskRepository;

    public ProcessInstanceServiceImpl(BpmnProcessRepository bpmnProcessRepository,
                                       ProcessInstanceRepository processInstanceRepository,
                                       TaskRepository taskRepository) {
        this.bpmnProcessRepository = bpmnProcessRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public ProcessInstanceResponse startInstance(StartProcessInstanceRequest request, String requesterUsername) {
        if (request.getProcessId() == null || request.getProcessId().isBlank()) {
            throw new AppException("processId must not be empty", 400);
        }

        BpmnProcess process = bpmnProcessRepository.findById(request.getProcessId())
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + request.getProcessId(), 404));
        if (process.getBpmnXml() == null || process.getBpmnXml().isBlank()) {
            throw new AppException("BPMN process has no XML to execute", 400);
        }

        BpmnProcessDefinition definition = BpmnGraphParser.parse(process.getBpmnXml());
        Map<String, Object> variables = request.getVariables() != null
                ? new HashMap<>(request.getVariables()) : new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = new ProcessInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setProcessId(process.getId());
        instance.setProcessVersion(process.getVersion());
        instance.setStatus("RUNNING");
        instance.setVariables(variables);
        instance.setStartedBy(requesterUsername);
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);

        // Insert the instance row first - any task created below has a FK to it.
        processInstanceRepository.save(instance);

        AdvanceResult result = ProcessEngine.advance(definition, definition.getStartNodeId(), variables);
        applyAdvanceResult(instance, definition, result);

        ProcessInstance saved = processInstanceRepository.save(instance);
        logger.info("Started process instance {} for BPMN process {}", saved.getId(), saved.getProcessId());
        return ProcessInstanceMapper.toResponse(saved);
    }

    @Override
    public ProcessInstanceResponse getInstanceById(String id) {
        ProcessInstance instance = processInstanceRepository.findById(id)
                .orElseThrow(() -> new AppException("Process instance not found with id: " + id, 404));
        return ProcessInstanceMapper.toResponse(instance);
    }

    @Override
    public PageResponse<ProcessInstanceResponse> listInstances(int page, int size) {
        List<ProcessInstanceResponse> content = processInstanceRepository.findPage(size, (page - 1) * size).stream()
                .map(ProcessInstanceMapper::toResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(content, page, size, processInstanceRepository.count());
    }

    private void applyAdvanceResult(ProcessInstance instance, BpmnProcessDefinition definition, AdvanceResult result) {
        LocalDateTime now = LocalDateTime.now();
        if (result.isCompleted()) {
            instance.setStatus("COMPLETED");
            instance.setCurrentNodeId(null);
            instance.setCompletedAt(now);
        } else {
            instance.setStatus("RUNNING");
            instance.setCurrentNodeId(result.getNextNodeId());
            createTaskForNode(instance, definition, result.getNextNodeId());
        }
        instance.setUpdatedAt(now);
    }

    private void createTaskForNode(ProcessInstance instance, BpmnProcessDefinition definition, String nodeId) {
        BpmnNode node = definition.getNode(nodeId);
        LocalDateTime now = LocalDateTime.now();

        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setProcessInstanceId(instance.getId());
        task.setNodeId(nodeId);
        task.setName(node != null && node.getName() != null ? node.getName() : nodeId);
        task.setStatus("PENDING");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);
    }
}
