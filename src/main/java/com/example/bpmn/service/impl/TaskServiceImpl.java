package com.example.bpmn.service.impl;

import com.example.bpmn.dto.CompleteTaskRequest;
import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.TaskResponse;
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
import com.example.bpmn.mapper.TaskMapper;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.ProcessInstanceTimer;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.ProcessInstanceTimerRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TaskServiceImpl implements TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);
    private final TaskRepository taskRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BpmnProcessVersionRepository bpmnProcessVersionRepository;
    private final ProcessInstanceTimerRepository processInstanceTimerRepository;
    private final EngineCallbacks engineCallbacks;

    public TaskServiceImpl(TaskRepository taskRepository,
                            ProcessInstanceRepository processInstanceRepository,
                            BpmnProcessVersionRepository bpmnProcessVersionRepository,
                            DmnDecisionService dmnDecisionService,
                            ProcessInstanceTimerRepository processInstanceTimerRepository,
                            ConnectorInvoker connectorInvoker) {
        this.taskRepository = taskRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.bpmnProcessVersionRepository = bpmnProcessVersionRepository;
        this.processInstanceTimerRepository = processInstanceTimerRepository;
        this.engineCallbacks = new EngineCallbacks(dmnDecisionService::evaluate, connectorInvoker);
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

        BpmnProcessDefinition definition = loadDefinition(instance);

        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED");
        task.setCompletedBy(requesterUserId);
        task.setCompletedAt(now);
        task.setUpdatedAt(now);
        Task savedTask = taskRepository.save(task);

        advanceAndPersist(instance, definition, task.getNodeId(), variables, now);

        logger.info("Task {} completed by {}, process instance {} now {}",
                savedTask.getId(), requesterUserId, instance.getId(), instance.getStatus());
        return TaskMapper.toResponse(savedTask);
    }

    @Override
    public void processDueTimers() {
        LocalDateTime now = LocalDateTime.now();
        for (Task task : taskRepository.findDueTimers(now)) {
            try {
                fireBoundaryTimer(task, now);
            } catch (Exception e) {
                logger.error("Failed to fire boundary timer for task {}: {}", task.getId(), e.getMessage(), e);
            }
        }
        for (ProcessInstanceTimer timer : processInstanceTimerRepository.findDueTimers(now)) {
            try {
                fireIntermediateTimer(timer, now);
            } catch (Exception e) {
                logger.error("Failed to fire intermediate timer {}: {}", timer.getId(), e.getMessage(), e);
            }
        }
    }

    private void fireIntermediateTimer(ProcessInstanceTimer timer, LocalDateTime now) {
        // Re-fetch: defensive against the row having been consumed already (single-threaded
        // poller today, but keeps this safe if that ever changes).
        if (processInstanceTimerRepository.findById(timer.getId()).isEmpty()) {
            return;
        }

        ProcessInstance instance = processInstanceRepository.findById(timer.getProcessInstanceId())
                .orElseThrow(() -> new AppException("Process instance not found with id: " + timer.getProcessInstanceId(), 404));
        BpmnProcessDefinition definition = loadDefinition(instance);
        Map<String, Object> variables = new HashMap<>(instance.getVariables() != null ? instance.getVariables() : Map.of());

        processInstanceTimerRepository.deleteById(timer.getId());
        advanceAndPersist(instance, definition, timer.getNodeId(), variables, now);

        logger.info("Intermediate timer fired for node {}, process instance {} now {}",
                timer.getNodeId(), instance.getId(), instance.getStatus());
    }

    private void fireBoundaryTimer(Task task, LocalDateTime now) {
        // Re-fetch: the task may have been claimed/completed by a human between the batch
        // query and now, in which case its timer no longer applies.
        Task current = taskRepository.findById(task.getId()).orElse(null);
        if (current == null || !("PENDING".equals(current.getStatus()) || "CLAIMED".equals(current.getStatus()))) {
            return;
        }

        ProcessInstance instance = processInstanceRepository.findById(current.getProcessInstanceId())
                .orElseThrow(() -> new AppException("Process instance not found with id: " + current.getProcessInstanceId(), 404));
        BpmnProcessDefinition definition = loadDefinition(instance);

        BpmnNode boundaryEvent = definition.getBoundaryTimerFor(current.getNodeId());
        if (boundaryEvent == null) {
            // Defensive: shouldn't happen since dueDate is only ever set for a node with a
            // boundary timer, but if the BPMN was edited/republished since, don't loop forever.
            logger.warn("Task {} has a due dueDate but node {} has no boundary timer in the current definition - clearing dueDate",
                    current.getId(), current.getNodeId());
            current.setDueDate(null);
            current.setUpdatedAt(now);
            taskRepository.save(current);
            return;
        }

        Map<String, Object> variables = new HashMap<>(instance.getVariables() != null ? instance.getVariables() : Map.of());

        if (boundaryEvent.isInterrupting()) {
            current.setStatus("CANCELLED");
        }
        rescheduleOrClearTimer(current, boundaryEvent, now);
        current.setUpdatedAt(now);
        taskRepository.save(current);

        advanceAndPersist(instance, definition, boundaryEvent.getId(), variables, now);

        logger.info("Boundary timer fired for task {} (node {}), process instance {} now {}",
                current.getId(), current.getNodeId(), instance.getId(), instance.getStatus());
    }

    /**
     * After a boundary timer fires, decides whether it should fire again. {@code null} remaining
     * means one-shot (timeDuration/timeDate, or an already-exhausted timeCycle) - clear the due
     * date so it never fires again. {@code -1} means an unbounded timeCycle - always reschedule.
     * A positive count is decremented; it reschedules while repeats remain, otherwise stops.
     */
    private void rescheduleOrClearTimer(Task task, BpmnNode boundaryEvent, LocalDateTime now) {
        Integer remaining = task.getTimerRepeatsRemaining();
        if (remaining == null) {
            task.setDueDate(null);
            return;
        }
        TimerCycle cycle = TimerCycle.parse(boundaryEvent.getTimerCycle());
        if (remaining == -1) {
            task.setDueDate(now.plus(cycle.interval()));
            return;
        }
        int next = remaining - 1;
        if (next > 0) {
            task.setDueDate(now.plus(cycle.interval()));
            task.setTimerRepeatsRemaining(next);
        } else {
            task.setDueDate(null);
            task.setTimerRepeatsRemaining(null);
        }
    }

    private BpmnProcessDefinition loadDefinition(ProcessInstance instance) {
        String bpmnXml = bpmnProcessVersionRepository.findByProcessIdAndVersion(instance.getProcessId(), instance.getProcessVersion())
                .map(version -> version.getBpmnXml())
                .orElseThrow(() -> new AppException("BPMN version snapshot not found for process "
                        + instance.getProcessId() + " v" + instance.getProcessVersion(), 500));
        return BpmnGraphParser.parse(bpmnXml);
    }

    /** Walks the engine forward from {@code fromNodeId}, persists any newly created tasks/timer waits, and updates the instance's status/currentNodeId/variables. Shared by task completion, boundary-timer firing, and intermediate-timer firing. */
    private void advanceAndPersist(ProcessInstance instance, BpmnProcessDefinition definition, String fromNodeId,
                                    Map<String, Object> variables, LocalDateTime now) {
        Set<String> pendingJoinArrivals = instance.getPendingJoinArrivals() != null
                ? instance.getPendingJoinArrivals() : Set.of();
        // Other branches still open elsewhere in this instance - an inclusive join needs these
        // to tell which of its incoming flows are still expected to arrive. The task/branch this
        // call originates from has already been saved/removed as COMPLETED/CANCELLED/consumed by
        // the caller, so it's naturally excluded here.
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
            // The task/timer that triggered this walk has already been consumed, so the failure is
            // parked on the instance instead of being thrown - otherwise the branch would be lost.
            // The variables submitted when completing the task are kept, so resuming doesn't ask
            // for them again; only values a later step would have derived from them are missing.
            instance.setVariables(variables);
            instance.markFailed(e.getNodeId(), e.getMessage(), now);
            logger.error("Process instance {} failed at service task {}: {}", instance.getId(), e.getNodeId(), e.getMessage(), e);
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

        // A parallel fork can leave other sibling tasks/timer waits (created in this same instance
        // by an earlier or later branch) still open, so instance completion depends on all of them,
        // not just the branch this call just walked.
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
        next.setDueDate(computeBoundaryTimerDueDate(definition, nodeId, now));
        next.setTimerRepeatsRemaining(computeInitialTimerRepeats(definition, nodeId));
        taskRepository.save(next);
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

    /** Computes when this task's attached boundary timer should first fire, or null if it has none. */
    static LocalDateTime computeBoundaryTimerDueDate(BpmnProcessDefinition definition, String nodeId, LocalDateTime now) {
        BpmnNode boundaryEvent = definition.getBoundaryTimerFor(nodeId);
        return boundaryEvent == null ? null : TimerSchedule.computeNextFireAt(boundaryEvent, now);
    }

    /** {@code null} if this task has no boundary timer or it isn't a timeCycle; {@code -1} if the cycle is unbounded; else the bounded repeat count. */
    static Integer computeInitialTimerRepeats(BpmnProcessDefinition definition, String nodeId) {
        BpmnNode boundaryEvent = definition.getBoundaryTimerFor(nodeId);
        return boundaryEvent == null ? null : TimerSchedule.computeInitialRepeats(boundaryEvent);
    }
}
