package com.example.bpmn.service.impl;

import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.ProcessIncidentResponse;
import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.dto.StartProcessInstanceRequest;
import com.example.bpmn.engine.AdvanceResult;
import com.example.bpmn.engine.BpmnGraphParser;
import com.example.bpmn.engine.BpmnNode;
import com.example.bpmn.engine.BpmnProcessDefinition;
import com.example.bpmn.engine.ConnectorException;
import com.example.bpmn.engine.ConnectorInvoker;
import com.example.bpmn.engine.EngineCallbacks;
import com.example.bpmn.engine.ProcessEngine;
import com.example.bpmn.engine.TimerCycle;
import com.example.bpmn.engine.TimerSchedule;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.ProcessInstanceMapper;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.BpmnProcessStartTimer;
import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.ProcessInstanceTimer;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.BpmnProcessStartTimerRepository;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.ProcessInstanceTimerRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.ProcessInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessInstanceServiceImpl implements ProcessInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceServiceImpl.class);
    private static final String SYSTEM_TIMER_STARTED_BY = "SYSTEM_TIMER";

    private final BpmnProcessRepository bpmnProcessRepository;
    private final BpmnProcessVersionRepository bpmnProcessVersionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskRepository taskRepository;
    private final ProcessInstanceTimerRepository processInstanceTimerRepository;
    private final BpmnProcessStartTimerRepository bpmnProcessStartTimerRepository;
    private final EngineCallbacks engineCallbacks;

    public ProcessInstanceServiceImpl(BpmnProcessRepository bpmnProcessRepository,
                                       BpmnProcessVersionRepository bpmnProcessVersionRepository,
                                       ProcessInstanceRepository processInstanceRepository,
                                       TaskRepository taskRepository,
                                       DmnDecisionService dmnDecisionService,
                                       ProcessInstanceTimerRepository processInstanceTimerRepository,
                                       BpmnProcessStartTimerRepository bpmnProcessStartTimerRepository,
                                       ConnectorInvoker connectorInvoker) {
        this.bpmnProcessRepository = bpmnProcessRepository;
        this.bpmnProcessVersionRepository = bpmnProcessVersionRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.taskRepository = taskRepository;
        this.processInstanceTimerRepository = processInstanceTimerRepository;
        this.bpmnProcessStartTimerRepository = bpmnProcessStartTimerRepository;
        this.engineCallbacks = new EngineCallbacks(dmnDecisionService::evaluate, connectorInvoker);
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

        Map<String, Object> variables = request.getVariables() != null
                ? new HashMap<>(request.getVariables()) : new HashMap<>();

        ProcessInstance saved = createAndAdvanceInstance(process, variables, requesterUsername);
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
    public PageResponse<ProcessInstanceResponse> listInstances(int page, int size, String status, String search) {
        String statusFilter = status != null && !status.isBlank() ? status.trim() : null;
        String searchFilter = search != null && !search.isBlank() ? search.trim().toLowerCase(Locale.ROOT) : null;

        // Filtering happens in memory rather than in SQL, same tradeoff listTasks already makes -
        // acceptable while instance volume stays small, worth revisiting if it doesn't.
        List<ProcessInstance> filtered = processInstanceRepository.findAll().stream()
                .filter(instance -> statusFilter == null || statusFilter.equalsIgnoreCase(instance.getStatus()))
                .filter(instance -> searchFilter == null || matchesSearch(instance, searchFilter))
                .sorted(Comparator.comparing(ProcessInstance::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int fromIndex = Math.min((page - 1) * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<ProcessInstanceResponse> content = filtered.subList(fromIndex, toIndex).stream()
                .map(ProcessInstanceMapper::toResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(content, page, size, filtered.size());
    }

    private static boolean matchesSearch(ProcessInstance instance, String lowercaseTerm) {
        return containsIgnoreCase(instance.getId(), lowercaseTerm)
                || containsIgnoreCase(instance.getProcessId(), lowercaseTerm)
                || containsIgnoreCase(instance.getStartedBy(), lowercaseTerm);
    }

    private static boolean containsIgnoreCase(String value, String lowercaseTerm) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowercaseTerm);
    }

    @Override
    public List<ProcessIncidentResponse> getIncidents(String instanceId) {
        ProcessInstance instance = processInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new AppException("Process instance not found with id: " + instanceId, 404));
        if (instance.getIncidentNodeId() == null) {
            return List.of();
        }

        ProcessIncidentResponse incident = new ProcessIncidentResponse();
        // Phase 1 records at most one open incident per instance, on the instance row itself -
        // so the instance id doubles as the incident id until that changes.
        incident.setId(instance.getId());
        incident.setProcessInstanceId(instance.getId());
        incident.setActivityId(instance.getIncidentNodeId());
        incident.setActivityName(resolveNodeName(instance, instance.getIncidentNodeId()));
        incident.setErrorType("CONNECTOR_FAILURE");
        incident.setErrorMessage(instance.getIncidentMessage());
        incident.setCreationTime(instance.getUpdatedAt());
        incident.setState("OPEN");
        return List.of(incident);
    }

    /** Best-effort display name for an incident's node - falls back to the node id itself rather than failing the request. */
    private String resolveNodeName(ProcessInstance instance, String nodeId) {
        try {
            return bpmnProcessVersionRepository.findByProcessIdAndVersion(instance.getProcessId(), instance.getProcessVersion())
                    .map(version -> BpmnGraphParser.parse(version.getBpmnXml()).getNode(nodeId))
                    .map(BpmnNode::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(nodeId);
        } catch (RuntimeException e) {
            logger.warn("Could not resolve display name for node {} of process instance {}: {}",
                    nodeId, instance.getId(), e.getMessage());
            return nodeId;
        }
    }

    @Override
    public ProcessInstanceResponse retryIncident(String instanceId, String requesterUsername) {
        ProcessInstance instance = processInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new AppException("Process instance not found with id: " + instanceId, 404));
        if (!"FAILED".equals(instance.getStatus()) || instance.getIncidentNodeId() == null) {
            throw new AppException("Process instance " + instanceId
                    + " has no incident to retry (status: " + instance.getStatus() + ")", 409);
        }

        BpmnProcessDefinition definition = loadVersionedDefinition(instance);
        String incidentNodeId = instance.getIncidentNodeId();
        Map<String, Object> variables = new HashMap<>(instance.getVariables() != null ? instance.getVariables() : Map.of());

        retryAndPersist(instance, definition, incidentNodeId, variables, LocalDateTime.now());

        logger.info("Retried process instance {} from incident node {} (requested by {}), now {}",
                instance.getId(), incidentNodeId, requesterUsername, instance.getStatus());
        return ProcessInstanceMapper.toResponse(instance);
    }

    /** Loads the exact BPMN XML this instance was started with, not whatever the live process currently is - execution correctness depends on it, unlike the display-only lookup in {@link #resolveNodeName}. */
    private BpmnProcessDefinition loadVersionedDefinition(ProcessInstance instance) {
        String bpmnXml = bpmnProcessVersionRepository.findByProcessIdAndVersion(instance.getProcessId(), instance.getProcessVersion())
                .map(BpmnProcessVersion::getBpmnXml)
                .orElseThrow(() -> new AppException("BPMN version snapshot not found for process "
                        + instance.getProcessId() + " v" + instance.getProcessVersion(), 500));
        return BpmnGraphParser.parse(bpmnXml);
    }

    /**
     * Same shape as {@code TaskServiceImpl.advanceAndPersist}: unlike {@link #applyAdvanceResult}
     * (only ever used right after creating a brand-new instance, which by construction has no
     * other open branches yet), a retried instance may already have sibling tasks/timer waits from
     * branches that advanced before the incident - so completion has to be decided from every open
     * task/timer the instance has, not just the ones this call produces.
     */
    private void retryAndPersist(ProcessInstance instance, BpmnProcessDefinition definition, String fromNodeId,
                                  Map<String, Object> variables, LocalDateTime now) {
        Set<String> pendingJoinArrivals = instance.getPendingJoinArrivals() != null
                ? instance.getPendingJoinArrivals() : Set.of();
        Set<String> otherActiveNodeIds = new HashSet<>(taskRepository.findByProcessInstanceId(instance.getId()).stream()
                .filter(t -> "PENDING".equals(t.getStatus()) || "CLAIMED".equals(t.getStatus()))
                .map(Task::getNodeId)
                .collect(Collectors.toSet()));
        otherActiveNodeIds.addAll(processInstanceTimerRepository.findByProcessInstanceId(instance.getId()).stream()
                .map(ProcessInstanceTimer::getNodeId)
                .collect(Collectors.toSet()));

        AdvanceResult result;
        try {
            result = ProcessEngine.advance(definition, fromNodeId, variables, pendingJoinArrivals,
                    otherActiveNodeIds, engineCallbacks);
        } catch (ConnectorException e) {
            instance.setVariables(variables);
            instance.markFailed(e.getNodeId(), e.getMessage(), now);
            logger.warn("Retry of process instance {} failed again at service task {}: {}",
                    instance.getId(), e.getNodeId(), e.getMessage());
            processInstanceRepository.save(instance);
            return;
        }

        instance.setVariables(result.getUpdatedVariables());
        instance.clearIncident();
        for (String nodeId : result.getNewUserTaskNodeIds()) {
            createTaskForNode(instance, definition, nodeId);
        }
        for (String nodeId : result.getNewTimerWaitNodeIds()) {
            createTimerWaitForNode(instance, definition, nodeId);
        }
        instance.setPendingJoinArrivals(result.getPendingJoinArrivals());

        List<Task> openTasks = taskRepository.findByProcessInstanceId(instance.getId()).stream()
                .filter(t -> "PENDING".equals(t.getStatus()) || "CLAIMED".equals(t.getStatus()))
                .collect(Collectors.toList());
        List<ProcessInstanceTimer> openTimerWaits = processInstanceTimerRepository.findByProcessInstanceId(instance.getId());

        if (openTasks.isEmpty() && openTimerWaits.isEmpty() && result.getPendingJoinArrivals().isEmpty()) {
            instance.setStatus("COMPLETED");
            instance.setCurrentNodeId(null);
            instance.setCompletedAt(now);
        } else {
            instance.setStatus("RUNNING");
            instance.setCurrentNodeId(Stream.concat(
                            openTasks.stream().map(Task::getNodeId),
                            openTimerWaits.stream().map(ProcessInstanceTimer::getNodeId))
                    .collect(Collectors.joining(",")));
        }
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);
    }

    @Override
    public void processDueStartTimers() {
        LocalDateTime now = LocalDateTime.now();
        for (BpmnProcessStartTimer schedule : bpmnProcessStartTimerRepository.findDue(now)) {
            try {
                fireStartTimer(schedule, now);
            } catch (Exception e) {
                logger.error("Failed to fire start timer for process {}: {}", schedule.getProcessId(), e.getMessage(), e);
            }
        }
    }

    private void fireStartTimer(BpmnProcessStartTimer schedule, LocalDateTime now) {
        BpmnProcess process = bpmnProcessRepository.findById(schedule.getProcessId()).orElse(null);
        if (process == null) {
            bpmnProcessStartTimerRepository.deleteByProcessId(schedule.getProcessId());
            return;
        }

        BpmnNode startNode;
        try {
            BpmnProcessDefinition definition = BpmnGraphParser.parse(process.getBpmnXml());
            startNode = definition.getNode(definition.getStartNodeId());
        } catch (Exception e) {
            logger.warn("Process {} has invalid BPMN XML - disabling its start-timer schedule: {}",
                    process.getId(), e.getMessage());
            bpmnProcessStartTimerRepository.deleteByProcessId(schedule.getProcessId());
            return;
        }
        boolean stillHasTimer = startNode.getTimerDate() != null || startNode.getTimerDuration() != null
                || startNode.getTimerCycle() != null;
        if (!stillHasTimer) {
            // The live XML is the source of truth, not the schedule row - it was edited since.
            bpmnProcessStartTimerRepository.deleteByProcessId(schedule.getProcessId());
            return;
        }

        ProcessInstance created = createAndAdvanceInstance(process, new HashMap<>(), SYSTEM_TIMER_STARTED_BY);
        logger.info("Auto-started process instance {} for process {} from its start timer", created.getId(), process.getId());

        rescheduleOrClearStartTimer(schedule, startNode, now);
    }

    /**
     * After a start timer fires, decides whether it should fire again - same decrement pattern as
     * {@code TaskServiceImpl.rescheduleOrClearTimer}, applied to a process schedule instead of a task.
     */
    private void rescheduleOrClearStartTimer(BpmnProcessStartTimer schedule, BpmnNode startNode, LocalDateTime now) {
        Integer remaining = schedule.getRepeatsRemaining();
        if (remaining == null) {
            bpmnProcessStartTimerRepository.deleteByProcessId(schedule.getProcessId());
            return;
        }
        TimerCycle cycle = TimerCycle.parse(startNode.getTimerCycle());
        if (remaining == -1) {
            schedule.setNextFireAt(now.plus(cycle.interval()));
            schedule.setUpdatedAt(now);
            bpmnProcessStartTimerRepository.save(schedule);
            return;
        }
        int next = remaining - 1;
        if (next > 0) {
            schedule.setNextFireAt(now.plus(cycle.interval()));
            schedule.setRepeatsRemaining(next);
            schedule.setUpdatedAt(now);
            bpmnProcessStartTimerRepository.save(schedule);
        } else {
            bpmnProcessStartTimerRepository.deleteByProcessId(schedule.getProcessId());
        }
    }

    /** Creates a brand-new instance for {@code process}, walks it from the start node, and persists the result. Shared by manual starts and timer-triggered starts. */
    private ProcessInstance createAndAdvanceInstance(BpmnProcess process, Map<String, Object> initialVariables, String startedBy) {
        BpmnProcessDefinition definition = BpmnGraphParser.parse(process.getBpmnXml());
        Map<String, Object> variables = new HashMap<>(initialVariables);

        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = new ProcessInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setProcessId(process.getId());
        instance.setProcessVersion(process.getVersion());
        instance.setStatus("RUNNING");
        instance.setVariables(variables);
        instance.setStartedBy(startedBy);
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);

        // Insert the instance row first - any task/timer wait created below has a FK to it.
        processInstanceRepository.save(instance);

        AdvanceResult result;
        try {
            result = ProcessEngine.advance(definition, definition.getStartNodeId(), variables,
                    Set.of(), Set.of(), engineCallbacks);
        } catch (ConnectorException e) {
            // The instance row already exists, so the failure is recorded on it rather than
            // thrown away - the caller sees a FAILED instance explaining which node stopped it.
            instance.markFailed(e.getNodeId(), e.getMessage(), LocalDateTime.now());
            logger.error("Process instance {} failed at service task {}: {}", instance.getId(), e.getNodeId(), e.getMessage(), e);
            return processInstanceRepository.save(instance);
        }
        applyAdvanceResult(instance, definition, result);

        return processInstanceRepository.save(instance);
    }

    private void applyAdvanceResult(ProcessInstance instance, BpmnProcessDefinition definition, AdvanceResult result) {
        LocalDateTime now = LocalDateTime.now();
        for (String nodeId : result.getNewUserTaskNodeIds()) {
            createTaskForNode(instance, definition, nodeId);
        }
        for (String nodeId : result.getNewTimerWaitNodeIds()) {
            createTimerWaitForNode(instance, definition, nodeId);
        }
        instance.setVariables(result.getUpdatedVariables());
        instance.setPendingJoinArrivals(result.getPendingJoinArrivals());
        instance.clearIncident();
        if (result.isFullyResolved()) {
            instance.setStatus("COMPLETED");
            instance.setCurrentNodeId(null);
            instance.setCompletedAt(now);
        } else {
            instance.setStatus("RUNNING");
            instance.setCurrentNodeId(Stream.concat(
                            result.getNewUserTaskNodeIds().stream(),
                            result.getNewTimerWaitNodeIds().stream())
                    .collect(Collectors.joining(",")));
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
        task.setDueDate(TaskServiceImpl.computeBoundaryTimerDueDate(definition, nodeId, now));
        task.setTimerRepeatsRemaining(TaskServiceImpl.computeInitialTimerRepeats(definition, nodeId));
        taskRepository.save(task);
    }

    private void createTimerWaitForNode(ProcessInstance instance, BpmnProcessDefinition definition, String nodeId) {
        BpmnNode node = definition.getNode(nodeId);
        LocalDateTime now = LocalDateTime.now();

        ProcessInstanceTimer timer = new ProcessInstanceTimer();
        timer.setId(UUID.randomUUID().toString());
        timer.setProcessInstanceId(instance.getId());
        timer.setNodeId(nodeId);
        timer.setDueDate(TimerSchedule.computeNextFireAt(node, now));
        timer.setCreatedAt(now);
        processInstanceTimerRepository.save(timer);
    }
}
