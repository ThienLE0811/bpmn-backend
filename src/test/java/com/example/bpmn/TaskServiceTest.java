package com.example.bpmn;

import com.example.bpmn.dto.CompleteTaskRequest;
import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;
import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.TaskResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.TaskService;
import com.example.bpmn.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TaskServiceTest {

    private static final String SIMPLE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="simple_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Do Thing" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private static final String GATEWAY_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="approval_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="flow1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Review Request" />
                <sequenceFlow id="flow2" sourceRef="task1" targetRef="gw1" />
                <exclusiveGateway id="gw1" name="Amount Check" default="flow4" />
                <sequenceFlow id="flow3" sourceRef="gw1" targetRef="task2">
                  <conditionExpression>${amount > 1000}</conditionExpression>
                </sequenceFlow>
                <userTask id="task2" name="Manager Approval" />
                <sequenceFlow id="flow5" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Approved (Manager)" />
                <sequenceFlow id="flow4" sourceRef="gw1" targetRef="end1" />
                <endEvent id="end1" name="Approved (Auto)" />
              </process>
            </definitions>
            """;

    private static final String PARALLEL_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="parallel_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Prepare" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="pgFork" />
                <parallelGateway id="pgFork" name="Fork" />
                <sequenceFlow id="f3" sourceRef="pgFork" targetRef="task2" />
                <sequenceFlow id="f4" sourceRef="pgFork" targetRef="task3" />
                <userTask id="task2" name="Branch A" />
                <userTask id="task3" name="Branch B" />
                <sequenceFlow id="f5" sourceRef="task2" targetRef="pgJoin" />
                <sequenceFlow id="f6" sourceRef="task3" targetRef="pgJoin" />
                <parallelGateway id="pgJoin" name="Join" />
                <sequenceFlow id="f7" sourceRef="pgJoin" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private static final String BOUNDARY_TIMER_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="boundary_timer_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Approve" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1" />
                <endEvent id="end1" name="Approved" />
                <boundaryEvent id="boundary1" attachedToRef="task1">
                  <timerEventDefinition><timeDuration>PT2H</timeDuration></timerEventDefinition>
                </boundaryEvent>
                <sequenceFlow id="f3" sourceRef="boundary1" targetRef="task2" />
                <userTask id="task2" name="Escalate" />
                <sequenceFlow id="f4" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Escalated" />
              </process>
            </definitions>
            """;

    private static final String NON_INTERRUPTING_BOUNDARY_TIMER_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="non_interrupting_boundary_timer_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Approve" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1" />
                <endEvent id="end1" name="Approved" />
                <boundaryEvent id="boundary1" attachedToRef="task1" cancelActivity="false">
                  <timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition>
                </boundaryEvent>
                <sequenceFlow id="f3" sourceRef="boundary1" targetRef="task2" />
                <userTask id="task2" name="Escalate" />
                <sequenceFlow id="f4" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Escalated" />
              </process>
            </definitions>
            """;

    private static final String BOUNDED_CYCLE_BOUNDARY_TIMER_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="bounded_cycle_boundary_timer_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Approve" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1" />
                <endEvent id="end1" name="Approved" />
                <boundaryEvent id="boundary1" attachedToRef="task1" cancelActivity="false">
                  <timerEventDefinition><timeCycle>R2/PT10M</timeCycle></timerEventDefinition>
                </boundaryEvent>
                <sequenceFlow id="f3" sourceRef="boundary1" targetRef="task2" />
                <userTask id="task2" name="Remind" />
                <sequenceFlow id="f4" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Reminded" />
              </process>
            </definitions>
            """;

    private static final String UNBOUNDED_CYCLE_BOUNDARY_TIMER_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="unbounded_cycle_boundary_timer_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Approve" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1" />
                <endEvent id="end1" name="Approved" />
                <boundaryEvent id="boundary1" attachedToRef="task1" cancelActivity="false">
                  <timerEventDefinition><timeCycle>R/PT10M</timeCycle></timerEventDefinition>
                </boundaryEvent>
                <sequenceFlow id="f3" sourceRef="boundary1" targetRef="task2" />
                <userTask id="task2" name="Remind" />
                <sequenceFlow id="f4" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Reminded" />
              </process>
            </definitions>
            """;

    private TaskService taskService;
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final List<BpmnProcessVersion> bpmnVersions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tasks.clear();
        instances.clear();
        bpmnVersions.clear();

        TaskRepository mockTaskRepo = new TaskRepository() {
            @Override
            public Task save(Task task) {
                tasks.put(task.getId(), task);
                return task;
            }

            @Override
            public Optional<Task> findById(String id) {
                return Optional.ofNullable(tasks.get(id));
            }

            @Override
            public List<Task> findByProcessId(String processId) {
                return tasks.values().stream()
                        .filter(t -> processId.equals(t.getProcessId()))
                        .collect(Collectors.toList());
            }

            @Override
            public List<Task> findByAssigneeId(String assigneeId) {
                return tasks.values().stream()
                        .filter(t -> assigneeId.equals(t.getAssigneeId()))
                        .collect(Collectors.toList());
            }

            @Override
            public List<Task> findByProcessInstanceId(String processInstanceId) {
                return tasks.values().stream()
                        .filter(t -> processInstanceId.equals(t.getProcessInstanceId()))
                        .collect(Collectors.toList());
            }

            @Override
            public List<Task> findAll() {
                return new ArrayList<>(tasks.values());
            }

            @Override
            public boolean deleteById(String id) {
                return tasks.remove(id) != null;
            }

            @Override
            public List<Task> findDueTimers(java.time.LocalDateTime now) {
                return tasks.values().stream()
                        .filter(t -> t.getDueDate() != null && !t.getDueDate().isAfter(now))
                        .filter(t -> "PENDING".equals(t.getStatus()) || "CLAIMED".equals(t.getStatus()))
                        .collect(Collectors.toList());
            }
        };

        ProcessInstanceRepository mockInstanceRepo = new ProcessInstanceRepository() {
            @Override
            public ProcessInstance save(ProcessInstance instance) {
                instances.put(instance.getId(), instance);
                return instance;
            }

            @Override
            public Optional<ProcessInstance> findById(String id) {
                return Optional.ofNullable(instances.get(id));
            }

            @Override
            public List<ProcessInstance> findAll() {
                return new ArrayList<>(instances.values());
            }

            @Override
            public List<ProcessInstance> findPage(int limit, int offset) {
                List<ProcessInstance> all = findAll();
                int from = Math.min(offset, all.size());
                int to = Math.min(offset + limit, all.size());
                return new ArrayList<>(all.subList(from, to));
            }

            @Override
            public long count() {
                return instances.size();
            }

            @Override
            public boolean deleteById(String id) {
                return instances.remove(id) != null;
            }
        };

        BpmnProcessVersionRepository mockVersionRepo = new BpmnProcessVersionRepository() {
            @Override
            public BpmnProcessVersion save(BpmnProcessVersion version) {
                bpmnVersions.add(version);
                return version;
            }

            @Override
            public List<BpmnProcessVersion> findByProcessId(String processId) {
                return bpmnVersions.stream()
                        .filter(v -> processId.equals(v.getProcessId()))
                        .toList();
            }

            @Override
            public Optional<BpmnProcessVersion> findByProcessIdAndVersion(String processId, int version) {
                return bpmnVersions.stream()
                        .filter(v -> processId.equals(v.getProcessId()) && version == v.getVersion())
                        .findFirst();
            }
        };

        DmnDecisionService mockDmnService = new DmnDecisionService() {
            @Override
            public PageResponse<DmnDecisionResponse> getAllDecisions(int page, int size) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DmnDecisionResponse getDecisionById(String id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DmnDecisionResponse getDecisionByKey(String decisionKey) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DmnDecisionResponse createDecision(DmnDecisionRequest request, String requesterUsername) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DmnDecisionResponse updateDecision(String id, DmnDecisionUpdateRequest request, String requesterUsername, String requesterRole) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void deleteDecision(String id, String requesterUsername, String requesterRole) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, Object> evaluate(String decisionKey, Map<String, Object> variables) {
                throw new UnsupportedOperationException();
            }
        };

        taskService = new TaskServiceImpl(mockTaskRepo, mockInstanceRepo, mockVersionRepo, mockDmnService);
    }

    private ProcessInstance seedInstance(String processId, int version, String bpmnXml, String currentNodeId) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setProcessId(processId);
        instance.setProcessVersion(version);
        instance.setStatus("RUNNING");
        instance.setCurrentNodeId(currentNodeId);
        instance.setVariables(new HashMap<>());
        instances.put(instance.getId(), instance);

        BpmnProcessVersion v = new BpmnProcessVersion();
        v.setId(UUID.randomUUID().toString());
        v.setProcessId(processId);
        v.setVersion(version);
        v.setBpmnXml(bpmnXml);
        bpmnVersions.add(v);

        return instance;
    }

    private Task seedTask(ProcessInstance instance, String nodeId, String status, String claimedBy) {
        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        if (instance != null) {
            task.setProcessInstanceId(instance.getId());
        }
        task.setNodeId(nodeId);
        task.setName(nodeId);
        task.setStatus(status);
        task.setClaimedBy(claimedBy);
        tasks.put(task.getId(), task);
        return task;
    }

    @Test
    @DisplayName("Should get a task by id")
    void testGetTaskByIdSuccess() {
        Task task = seedTask(null, "task1", "PENDING", null);

        TaskResponse response = taskService.getTaskById(task.getId());

        assertEquals(task.getId(), response.getId());
        assertEquals("task1", response.getNodeId());
    }

    @Test
    @DisplayName("Should throw 404 when getting an unknown task")
    void testGetTaskByIdNotFound() {
        AppException ex = assertThrows(AppException.class, () -> taskService.getTaskById("unknown"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should claim a pending task")
    void testClaimTaskSuccess() {
        Task task = seedTask(null, "task1", "PENDING", null);

        TaskResponse response = taskService.claimTask(task.getId(), "alice");

        assertEquals("CLAIMED", response.getStatus());
        assertEquals("alice", response.getClaimedBy());
        assertNotNull(response.getClaimedAt());
    }

    @Test
    @DisplayName("Should reject claiming an already-claimed task")
    void testClaimTaskAlreadyClaimed() {
        Task task = seedTask(null, "task1", "CLAIMED", "bob");

        AppException ex = assertThrows(AppException.class, () -> taskService.claimTask(task.getId(), "alice"));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should throw 404 when claiming an unknown task")
    void testClaimTaskNotFound() {
        AppException ex = assertThrows(AppException.class, () -> taskService.claimTask("unknown", "alice"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should throw 404 when completing an unknown task")
    void testCompleteTaskNotFound() {
        AppException ex = assertThrows(AppException.class,
                () -> taskService.completeTask("unknown", "alice", "USER", new CompleteTaskRequest()));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject completing a task that has not been claimed yet")
    void testCompleteTaskNotClaimedYet() {
        Task task = seedTask(null, "task1", "PENDING", null);

        AppException ex = assertThrows(AppException.class,
                () -> taskService.completeTask(task.getId(), "alice", "USER", new CompleteTaskRequest()));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject completing a task claimed by someone else, for a non-admin")
    void testCompleteTaskByNonClaimerNonAdmin() {
        ProcessInstance instance = seedInstance("simple_process", 1, SIMPLE_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "CLAIMED", "bob");

        AppException ex = assertThrows(AppException.class,
                () -> taskService.completeTask(task.getId(), "alice", "USER", new CompleteTaskRequest()));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should allow an admin to complete a task claimed by someone else")
    void testCompleteTaskByAdminEvenIfNotClaimer() {
        ProcessInstance instance = seedInstance("simple_process", 1, SIMPLE_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "CLAIMED", "bob");

        TaskResponse response = taskService.completeTask(task.getId(), "alice", "ADMIN", new CompleteTaskRequest());

        assertEquals("COMPLETED", response.getStatus());
        assertEquals("alice", response.getCompletedBy());
    }

    @Test
    @DisplayName("Should throw 500 when the BPMN version snapshot is missing")
    void testCompleteTaskMissingBpmnVersionSnapshot() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setProcessId("ghost_process");
        instance.setProcessVersion(1);
        instance.setStatus("RUNNING");
        instance.setVariables(new HashMap<>());
        instances.put(instance.getId(), instance);
        Task task = seedTask(instance, "task1", "CLAIMED", "alice");

        AppException ex = assertThrows(AppException.class,
                () -> taskService.completeTask(task.getId(), "alice", "USER", new CompleteTaskRequest()));
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should complete the instance when finishing the last task of a simple linear process")
    void testCompleteTaskFinishesSimpleProcess() {
        ProcessInstance instance = seedInstance("simple_process", 1, SIMPLE_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "CLAIMED", "alice");

        taskService.completeTask(task.getId(), "alice", "USER", new CompleteTaskRequest());

        ProcessInstance updated = instances.get(instance.getId());
        assertEquals("COMPLETED", updated.getStatus());
        assertNotNull(updated.getCompletedAt());
        assertTrue(tasks.values().stream().noneMatch(t -> "PENDING".equals(t.getStatus())));
    }

    @Test
    @DisplayName("Should merge completion variables and take the default gateway flow when condition is false")
    void testCompleteTaskGatewayDefaultFlow() {
        ProcessInstance instance = seedInstance("approval_process", 1, GATEWAY_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "CLAIMED", "alice");

        CompleteTaskRequest request = new CompleteTaskRequest();
        request.setVariables(Map.of("amount", 500));
        taskService.completeTask(task.getId(), "alice", "USER", request);

        ProcessInstance updated = instances.get(instance.getId());
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals(500, updated.getVariables().get("amount"));
    }

    @Test
    @DisplayName("Should create the manager-approval task when the gateway condition is true")
    void testCompleteTaskGatewayConditionedFlow() {
        ProcessInstance instance = seedInstance("approval_process", 1, GATEWAY_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "CLAIMED", "alice");

        CompleteTaskRequest request = new CompleteTaskRequest();
        request.setVariables(Map.of("amount", 5000));
        taskService.completeTask(task.getId(), "alice", "USER", request);

        ProcessInstance updated = instances.get(instance.getId());
        assertEquals("RUNNING", updated.getStatus());
        List<Task> openTasks = tasks.values().stream().filter(t -> "PENDING".equals(t.getStatus())).toList();
        assertEquals(1, openTasks.size());
        assertEquals("task2", openTasks.get(0).getNodeId());
    }

    @Test
    @DisplayName("Should fork into two parallel tasks when completing the task before a parallel gateway")
    void testCompleteTaskForkCreatesParallelBranches() {
        ProcessInstance instance = seedInstance("parallel_process", 1, PARALLEL_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "CLAIMED", "alice");

        taskService.completeTask(task.getId(), "alice", "USER", new CompleteTaskRequest());

        ProcessInstance updated = instances.get(instance.getId());
        assertEquals("RUNNING", updated.getStatus());
        List<String> openNodeIds = tasks.values().stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .map(Task::getNodeId)
                .sorted()
                .toList();
        assertEquals(List.of("task2", "task3"), openNodeIds);
    }

    @Test
    @DisplayName("Should keep the instance running while a sibling parallel branch is still open")
    void testCompleteTaskJoinWaitsForSiblingBranch() {
        ProcessInstance instance = seedInstance("parallel_process", 1, PARALLEL_PROCESS_XML, "task1");
        Task forkTask = seedTask(instance, "task1", "CLAIMED", "alice");
        taskService.completeTask(forkTask.getId(), "alice", "USER", new CompleteTaskRequest());

        Task branchA = tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).findFirst().orElseThrow();
        branchA.setStatus("CLAIMED");
        branchA.setClaimedBy("alice");

        taskService.completeTask(branchA.getId(), "alice", "USER", new CompleteTaskRequest());

        ProcessInstance updated = instances.get(instance.getId());
        assertEquals("RUNNING", updated.getStatus());
        assertFalse(updated.getPendingJoinArrivals().isEmpty());
        List<Task> openTasks = tasks.values().stream().filter(t -> "PENDING".equals(t.getStatus()) || "CLAIMED".equals(t.getStatus())).toList();
        assertEquals(1, openTasks.size());
        assertEquals("task3", openTasks.get(0).getNodeId());
    }

    @Test
    @DisplayName("Should complete the instance once the last parallel branch reaches the join")
    void testCompleteTaskJoinCompletesInstanceWhenBothBranchesFinish() {
        ProcessInstance instance = seedInstance("parallel_process", 1, PARALLEL_PROCESS_XML, "task1");
        Task forkTask = seedTask(instance, "task1", "CLAIMED", "alice");
        taskService.completeTask(forkTask.getId(), "alice", "USER", new CompleteTaskRequest());

        Task branchA = tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).findFirst().orElseThrow();
        branchA.setStatus("CLAIMED");
        branchA.setClaimedBy("alice");
        taskService.completeTask(branchA.getId(), "alice", "USER", new CompleteTaskRequest());

        Task branchB = tasks.values().stream().filter(t -> "task3".equals(t.getNodeId())).findFirst().orElseThrow();
        branchB.setStatus("CLAIMED");
        branchB.setClaimedBy("alice");
        taskService.completeTask(branchB.getId(), "alice", "USER", new CompleteTaskRequest());

        ProcessInstance updated = instances.get(instance.getId());
        assertEquals("COMPLETED", updated.getStatus());
        assertTrue(updated.getPendingJoinArrivals() == null || updated.getPendingJoinArrivals().isEmpty());
    }

    @Test
    @DisplayName("Should cancel a task past its boundary timer deadline and create the escalation task")
    void testProcessDueTimersCancelsTaskAndEscalates() {
        ProcessInstance instance = seedInstance("boundary_timer_process", 1, BOUNDARY_TIMER_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "PENDING", null);
        task.setDueDate(java.time.LocalDateTime.now().minusMinutes(5));

        taskService.processDueTimers();

        Task updatedTask = tasks.get(task.getId());
        assertEquals("CANCELLED", updatedTask.getStatus());

        ProcessInstance updatedInstance = instances.get(instance.getId());
        assertEquals("RUNNING", updatedInstance.getStatus());
        assertEquals("task2", updatedInstance.getCurrentNodeId());

        List<Task> escalationTasks = tasks.values().stream()
                .filter(t -> "task2".equals(t.getNodeId()))
                .toList();
        assertEquals(1, escalationTasks.size());
        assertEquals("PENDING", escalationTasks.get(0).getStatus());
    }

    @Test
    @DisplayName("Should leave a task with a future due date untouched")
    void testProcessDueTimersIgnoresFutureDueDate() {
        ProcessInstance instance = seedInstance("boundary_timer_process", 1, BOUNDARY_TIMER_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "PENDING", null);
        task.setDueDate(java.time.LocalDateTime.now().plusHours(1));

        taskService.processDueTimers();

        assertEquals("PENDING", tasks.get(task.getId()).getStatus());
        assertTrue(tasks.values().stream().noneMatch(t -> "task2".equals(t.getNodeId())));
    }

    @Test
    @DisplayName("Should skip a task that was already completed before its timer was processed")
    void testProcessDueTimersSkipsAlreadyCompletedTask() {
        ProcessInstance instance = seedInstance("boundary_timer_process", 1, BOUNDARY_TIMER_PROCESS_XML, null);
        Task task = seedTask(instance, "task1", "COMPLETED", "alice");
        task.setDueDate(java.time.LocalDateTime.now().minusMinutes(5));

        assertDoesNotThrow(() -> taskService.processDueTimers());

        assertEquals("COMPLETED", tasks.get(task.getId()).getStatus());
        assertTrue(tasks.values().stream().noneMatch(t -> "task2".equals(t.getNodeId())));
    }

    @Test
    @DisplayName("Should keep a non-interrupting boundary timer's task open and only fork the escalation branch")
    void testProcessDueTimersNonInterruptingKeepsTaskOpenAndEscalates() {
        ProcessInstance instance = seedInstance("non_interrupting_boundary_timer_process", 1,
                NON_INTERRUPTING_BOUNDARY_TIMER_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "PENDING", null);
        task.setDueDate(java.time.LocalDateTime.now().minusMinutes(5));

        taskService.processDueTimers();

        Task updatedTask = tasks.get(task.getId());
        assertEquals("PENDING", updatedTask.getStatus());
        assertNull(updatedTask.getDueDate());

        List<Task> escalationTasks = tasks.values().stream()
                .filter(t -> "task2".equals(t.getNodeId()))
                .toList();
        assertEquals(1, escalationTasks.size());
        assertEquals("PENDING", escalationTasks.get(0).getStatus());

        ProcessInstance updatedInstance = instances.get(instance.getId());
        assertEquals("RUNNING", updatedInstance.getStatus());

        // Firing again without a new due date must not create a second escalation task.
        taskService.processDueTimers();
        assertEquals(1, tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).count());
    }

    @Test
    @DisplayName("Should fire a bounded timeCycle exactly N times then stop rescheduling")
    void testProcessDueTimersBoundedCycleFiresExactlyNTimesThenStops() {
        ProcessInstance instance = seedInstance("bounded_cycle_boundary_timer_process", 1,
                BOUNDED_CYCLE_BOUNDARY_TIMER_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "PENDING", null);
        task.setTimerRepeatsRemaining(2);
        task.setDueDate(java.time.LocalDateTime.now().minusMinutes(1));

        taskService.processDueTimers();
        Task afterFirstFire = tasks.get(task.getId());
        assertEquals("PENDING", afterFirstFire.getStatus());
        assertEquals(1, afterFirstFire.getTimerRepeatsRemaining());
        assertNotNull(afterFirstFire.getDueDate());
        assertTrue(afterFirstFire.getDueDate().isAfter(java.time.LocalDateTime.now()));
        assertEquals(1, tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).count());

        afterFirstFire.setDueDate(java.time.LocalDateTime.now().minusMinutes(1));
        taskService.processDueTimers();
        Task afterSecondFire = tasks.get(task.getId());
        assertEquals("PENDING", afterSecondFire.getStatus());
        assertNull(afterSecondFire.getTimerRepeatsRemaining());
        assertNull(afterSecondFire.getDueDate());
        assertEquals(2, tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).count());

        // Exhausted: no due date left, so a third poll must not fire again.
        taskService.processDueTimers();
        assertEquals(2, tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).count());
    }

    @Test
    @DisplayName("Should keep rescheduling an unbounded timeCycle indefinitely")
    void testProcessDueTimersUnboundedCycleKeepsRescheduling() {
        ProcessInstance instance = seedInstance("unbounded_cycle_boundary_timer_process", 1,
                UNBOUNDED_CYCLE_BOUNDARY_TIMER_PROCESS_XML, "task1");
        Task task = seedTask(instance, "task1", "PENDING", null);
        task.setTimerRepeatsRemaining(-1);
        task.setDueDate(java.time.LocalDateTime.now().minusMinutes(1));

        for (int i = 1; i <= 3; i++) {
            taskService.processDueTimers();
            Task current = tasks.get(task.getId());
            assertEquals("PENDING", current.getStatus());
            assertEquals(-1, current.getTimerRepeatsRemaining());
            assertNotNull(current.getDueDate());
            assertTrue(current.getDueDate().isAfter(java.time.LocalDateTime.now()));
            assertEquals(i, tasks.values().stream().filter(t -> "task2".equals(t.getNodeId())).count());

            current.setDueDate(java.time.LocalDateTime.now().minusMinutes(1));
        }
    }

    @Test
    @DisplayName("Should filter tasks by status and by only-mine")
    void testListTasksFiltersByStatusAndMine() {
        seedTask(null, "task1", "PENDING", null);
        seedTask(null, "task2", "CLAIMED", "alice");
        seedTask(null, "task3", "COMPLETED", "alice");

        PageResponse<TaskResponse> claimedOnly = taskService.listTasks("CLAIMED", false, "alice", 1, 20);
        assertEquals(1, claimedOnly.getContent().size());
        assertEquals("task2", claimedOnly.getContent().get(0).getNodeId());

        PageResponse<TaskResponse> mine = taskService.listTasks(null, true, "alice", 1, 20);
        assertEquals(2, mine.getContent().size());
    }

    @Test
    @DisplayName("Should paginate task listings")
    void testListTasksPagination() {
        seedTask(null, "task1", "PENDING", null);
        seedTask(null, "task2", "PENDING", null);
        seedTask(null, "task3", "PENDING", null);

        PageResponse<TaskResponse> page1 = taskService.listTasks(null, false, "alice", 1, 2);
        assertEquals(2, page1.getContent().size());
        assertEquals(3, page1.getTotalElements());

        PageResponse<TaskResponse> page2 = taskService.listTasks(null, false, "alice", 2, 2);
        assertEquals(1, page2.getContent().size());
    }
}
