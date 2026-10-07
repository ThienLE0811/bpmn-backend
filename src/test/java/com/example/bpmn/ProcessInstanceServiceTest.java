package com.example.bpmn;

import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;
import com.example.bpmn.dto.PageResponse;
import com.example.bpmn.dto.ProcessInstanceResponse;
import com.example.bpmn.dto.StartProcessInstanceRequest;
import com.example.bpmn.engine.ConnectorInvoker;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.BpmnProcessStartTimer;
import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.ProcessInstanceTimer;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.BpmnProcessStartTimerRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.ProcessInstanceTimerRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.ProcessInstanceService;
import com.example.bpmn.service.impl.ProcessInstanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ProcessInstanceServiceTest {

    private static final String NO_USER_TASK_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="no_task_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

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
                <sequenceFlow id="flow1" sourceRef="start1" targetRef="gw1" />
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

    private static final String INTERMEDIATE_TIMER_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="intermediate_timer_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="wait1" />
                <intermediateCatchEvent id="wait1" name="Wait">
                  <timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition>
                </intermediateCatchEvent>
                <sequenceFlow id="f2" sourceRef="wait1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private static final String TIMER_START_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="timer_start_process" isExecutable="true">
                <startEvent id="start1" name="Start">
                  <timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition>
                </startEvent>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private static final String TIMER_START_BOUNDED_CYCLE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="timer_start_cycle_process" isExecutable="true">
                <startEvent id="start1" name="Start">
                  <timerEventDefinition><timeCycle>R2/PT10M</timeCycle></timerEventDefinition>
                </startEvent>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private static final String TIMER_START_UNBOUNDED_CYCLE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="timer_start_unbounded_process" isExecutable="true">
                <startEvent id="start1" name="Start">
                  <timerEventDefinition><timeCycle>R/PT10M</timeCycle></timerEventDefinition>
                </startEvent>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private static final String CONNECTOR_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="defs" targetNamespace="http://example.com">
              <process id="connector_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="call1" />
                <serviceTask id="call1" name="Check credit">
                  <extensionElements>
                    <camunda:connector>
                      <camunda:connectorId>http</camunda:connectorId>
                      <camunda:inputOutput>
                        <camunda:inputParameter name="url">https://api.example.com/credit</camunda:inputParameter>
                        <camunda:inputParameter name="customerId">${customerId}</camunda:inputParameter>
                        <camunda:outputParameter name="creditScore">${score}</camunda:outputParameter>
                      </camunda:inputOutput>
                    </camunda:connector>
                  </extensionElements>
                </serviceTask>
                <sequenceFlow id="f2" sourceRef="call1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    private ProcessInstanceService processInstanceService;
    /** Reassigned per test so the fake connector can succeed, fail, or record what it was handed. */
    private ConnectorInvoker connectorInvoker;
    private final List<Map<String, Object>> connectorCalls = new ArrayList<>();
    private final Map<String, BpmnProcess> processes = new ConcurrentHashMap<>();
    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, ProcessInstanceTimer> timerWaits = new ConcurrentHashMap<>();
    private final Map<String, BpmnProcessStartTimer> startTimers = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        processes.clear();
        instances.clear();
        tasks.clear();
        timerWaits.clear();
        startTimers.clear();
        connectorCalls.clear();
        connectorInvoker = (connectorId, inputs) -> Map.of();

        BpmnProcessRepository mockProcessRepo = new BpmnProcessRepository() {
            @Override
            public BpmnProcess save(BpmnProcess process) {
                processes.put(process.getId(), process);
                return process;
            }

            @Override
            public Optional<BpmnProcess> findById(String id) {
                return Optional.ofNullable(processes.get(id));
            }

            @Override
            public Optional<BpmnProcess> findByProcessKey(String processKey) {
                return processes.values().stream()
                        .filter(p -> processKey.equals(p.getProcessKey()))
                        .findFirst();
            }

            @Override
            public List<BpmnProcess> findAll() {
                return new ArrayList<>(processes.values());
            }

            @Override
            public List<BpmnProcess> findPage(int limit, int offset) {
                List<BpmnProcess> all = findAll();
                int from = Math.min(offset, all.size());
                int to = Math.min(offset + limit, all.size());
                return new ArrayList<>(all.subList(from, to));
            }

            @Override
            public long count() {
                return processes.size();
            }

            @Override
            public boolean deleteById(String id) {
                return processes.remove(id) != null;
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
                return tasks.values().stream().filter(t -> processId.equals(t.getProcessId())).toList();
            }

            @Override
            public List<Task> findByAssigneeId(String assigneeId) {
                return tasks.values().stream().filter(t -> assigneeId.equals(t.getAssigneeId())).toList();
            }

            @Override
            public List<Task> findByProcessInstanceId(String processInstanceId) {
                return tasks.values().stream().filter(t -> processInstanceId.equals(t.getProcessInstanceId())).toList();
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
                        .toList();
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

        ProcessInstanceTimerRepository mockTimerWaitRepo = new ProcessInstanceTimerRepository() {
            @Override
            public ProcessInstanceTimer save(ProcessInstanceTimer timer) {
                timerWaits.put(timer.getId(), timer);
                return timer;
            }

            @Override
            public Optional<ProcessInstanceTimer> findById(String id) {
                return Optional.ofNullable(timerWaits.get(id));
            }

            @Override
            public List<ProcessInstanceTimer> findByProcessInstanceId(String processInstanceId) {
                return timerWaits.values().stream().filter(t -> processInstanceId.equals(t.getProcessInstanceId())).toList();
            }

            @Override
            public List<ProcessInstanceTimer> findDueTimers(LocalDateTime now) {
                return timerWaits.values().stream().filter(t -> !t.getDueDate().isAfter(now)).toList();
            }

            @Override
            public boolean deleteById(String id) {
                return timerWaits.remove(id) != null;
            }
        };

        BpmnProcessStartTimerRepository mockStartTimerRepo = new BpmnProcessStartTimerRepository() {
            @Override
            public BpmnProcessStartTimer save(BpmnProcessStartTimer timer) {
                startTimers.put(timer.getProcessId(), timer);
                return timer;
            }

            @Override
            public Optional<BpmnProcessStartTimer> findByProcessId(String processId) {
                return Optional.ofNullable(startTimers.get(processId));
            }

            @Override
            public List<BpmnProcessStartTimer> findDue(LocalDateTime now) {
                return startTimers.values().stream().filter(t -> !t.getNextFireAt().isAfter(now)).toList();
            }

            @Override
            public void deleteByProcessId(String processId) {
                startTimers.remove(processId);
            }
        };

        processInstanceService = new ProcessInstanceServiceImpl(mockProcessRepo, mockInstanceRepo, mockTaskRepo, mockDmnService,
                mockTimerWaitRepo, mockStartTimerRepo, (connectorId, inputs) -> {
                    connectorCalls.add(inputs);
                    return connectorInvoker.invoke(connectorId, inputs);
                });
    }

    private BpmnProcessStartTimer seedStartTimer(String processId, LocalDateTime nextFireAt, Integer repeatsRemaining) {
        BpmnProcessStartTimer timer = new BpmnProcessStartTimer();
        timer.setProcessId(processId);
        timer.setNextFireAt(nextFireAt);
        timer.setRepeatsRemaining(repeatsRemaining);
        timer.setCreatedAt(LocalDateTime.now());
        timer.setUpdatedAt(LocalDateTime.now());
        startTimers.put(processId, timer);
        return timer;
    }

    private BpmnProcess seedProcess(String id, String bpmnXml) {
        BpmnProcess process = new BpmnProcess(id, "key_" + id, "Process " + id, 1, bpmnXml, "ACTIVE");
        processes.put(id, process);
        return process;
    }

    private StartProcessInstanceRequest startRequest(String processId, Map<String, Object> variables) {
        StartProcessInstanceRequest request = new StartProcessInstanceRequest();
        request.setProcessId(processId);
        request.setVariables(variables);
        return request;
    }

    @Test
    @DisplayName("Should start an instance and stop at the first user task")
    void testStartInstanceStopsAtFirstUserTask() {
        seedProcess("proc-1", SIMPLE_PROCESS_XML);

        ProcessInstanceResponse response = processInstanceService.startInstance(startRequest("proc-1", null), "alice");

        assertEquals("RUNNING", response.getStatus());
        assertEquals("proc-1", response.getProcessId());
        assertEquals(1, response.getProcessVersion());
        assertEquals("task1", response.getCurrentNodeId());
        assertEquals(1, tasks.size());
        Task created = tasks.values().iterator().next();
        assertEquals("PENDING", created.getStatus());
        assertEquals("task1", created.getNodeId());
        assertEquals(response.getId(), created.getProcessInstanceId());
    }

    @Test
    @DisplayName("Should immediately complete an instance whose process has no user task")
    void testStartInstanceCompletesWhenNoUserTask() {
        seedProcess("proc-1", NO_USER_TASK_PROCESS_XML);

        ProcessInstanceResponse response = processInstanceService.startInstance(startRequest("proc-1", null), "alice");

        assertEquals("COMPLETED", response.getStatus());
        assertNull(response.getCurrentNodeId());
        assertNotNull(response.getCompletedAt());
        assertTrue(tasks.isEmpty());
    }

    @Test
    @DisplayName("Should carry start variables through to a gateway condition and skip straight to auto-approval")
    void testStartInstanceGatewayDefaultFlow() {
        seedProcess("proc-1", GATEWAY_PROCESS_XML);

        ProcessInstanceResponse response = processInstanceService.startInstance(
                startRequest("proc-1", Map.of("amount", 500)), "alice");

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(500, response.getVariables().get("amount"));
        assertTrue(tasks.isEmpty());
    }

    @Test
    @DisplayName("Should create the manager-approval task when the start variables satisfy the gateway condition")
    void testStartInstanceGatewayConditionedFlow() {
        seedProcess("proc-1", GATEWAY_PROCESS_XML);

        ProcessInstanceResponse response = processInstanceService.startInstance(
                startRequest("proc-1", Map.of("amount", 5000)), "alice");

        assertEquals("RUNNING", response.getStatus());
        assertEquals("task2", response.getCurrentNodeId());
        assertEquals(1, tasks.size());
    }

    @Test
    @DisplayName("Should reject starting an instance with a blank processId")
    void testStartInstanceBlankProcessId() {
        AppException ex = assertThrows(AppException.class,
                () -> processInstanceService.startInstance(startRequest(" ", null), "alice"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should throw 404 when starting an instance for an unknown process")
    void testStartInstanceProcessNotFound() {
        AppException ex = assertThrows(AppException.class,
                () -> processInstanceService.startInstance(startRequest("unknown", null), "alice"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject starting an instance when the process has no BPMN XML")
    void testStartInstanceBlankBpmnXml() {
        seedProcess("proc-1", " ");

        AppException ex = assertThrows(AppException.class,
                () -> processInstanceService.startInstance(startRequest("proc-1", null), "alice"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should get an instance by id")
    void testGetInstanceByIdSuccess() {
        seedProcess("proc-1", SIMPLE_PROCESS_XML);
        ProcessInstanceResponse started = processInstanceService.startInstance(startRequest("proc-1", null), "alice");

        ProcessInstanceResponse fetched = processInstanceService.getInstanceById(started.getId());

        assertEquals(started.getId(), fetched.getId());
        assertEquals("RUNNING", fetched.getStatus());
    }

    @Test
    @DisplayName("Should throw 404 when getting an unknown instance")
    void testGetInstanceByIdNotFound() {
        AppException ex = assertThrows(AppException.class, () -> processInstanceService.getInstanceById("unknown"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should paginate instance listings")
    void testListInstancesPagination() {
        seedProcess("proc-1", SIMPLE_PROCESS_XML);
        processInstanceService.startInstance(startRequest("proc-1", null), "alice");
        processInstanceService.startInstance(startRequest("proc-1", null), "alice");
        processInstanceService.startInstance(startRequest("proc-1", null), "alice");

        PageResponse<ProcessInstanceResponse> page1 = processInstanceService.listInstances(1, 2);
        assertEquals(2, page1.getContent().size());
        assertEquals(3, page1.getTotalElements());

        PageResponse<ProcessInstanceResponse> page2 = processInstanceService.listInstances(2, 2);
        assertEquals(1, page2.getContent().size());
    }

    @Test
    @DisplayName("Should create a timer wait (not a task) when starting lands on an intermediate catch timer event")
    void testStartInstanceCreatesTimerWaitForIntermediateCatchEvent() {
        seedProcess("proc-1", INTERMEDIATE_TIMER_PROCESS_XML);

        ProcessInstanceResponse response = processInstanceService.startInstance(startRequest("proc-1", null), "alice");

        assertEquals("RUNNING", response.getStatus());
        assertEquals("wait1", response.getCurrentNodeId());
        assertTrue(tasks.isEmpty());
        assertEquals(1, timerWaits.size());
        ProcessInstanceTimer wait = timerWaits.values().iterator().next();
        assertEquals("wait1", wait.getNodeId());
        assertEquals(response.getId(), wait.getProcessInstanceId());
    }

    @Test
    @DisplayName("Should auto-start one instance for a one-shot start timer then remove its schedule")
    void testProcessDueStartTimersFiresOneShotThenRemovesSchedule() {
        seedProcess("proc-timer", TIMER_START_PROCESS_XML);
        seedStartTimer("proc-timer", LocalDateTime.now().minusMinutes(1), null);

        processInstanceService.processDueStartTimers();

        List<ProcessInstance> created = instances.values().stream()
                .filter(i -> "proc-timer".equals(i.getProcessId())).toList();
        assertEquals(1, created.size());
        assertEquals("SYSTEM_TIMER", created.get(0).getStartedBy());
        assertNull(startTimers.get("proc-timer"));
    }

    @Test
    @DisplayName("Should fire a bounded start-timer cycle exactly N times then remove its schedule")
    void testProcessDueStartTimersBoundedCycleFiresExactlyNTimesThenStops() {
        seedProcess("proc-cycle", TIMER_START_BOUNDED_CYCLE_PROCESS_XML);
        seedStartTimer("proc-cycle", LocalDateTime.now().minusMinutes(1), 2);

        processInstanceService.processDueStartTimers();
        assertEquals(1, instances.values().stream().filter(i -> "proc-cycle".equals(i.getProcessId())).count());
        BpmnProcessStartTimer afterFirst = startTimers.get("proc-cycle");
        assertNotNull(afterFirst);
        assertEquals(1, afterFirst.getRepeatsRemaining());
        assertTrue(afterFirst.getNextFireAt().isAfter(LocalDateTime.now()));

        afterFirst.setNextFireAt(LocalDateTime.now().minusMinutes(1));
        processInstanceService.processDueStartTimers();
        assertEquals(2, instances.values().stream().filter(i -> "proc-cycle".equals(i.getProcessId())).count());
        assertNull(startTimers.get("proc-cycle"));
    }

    @Test
    @DisplayName("Should keep rescheduling an unbounded start-timer cycle indefinitely")
    void testProcessDueStartTimersUnboundedCycleKeepsRescheduling() {
        seedProcess("proc-unbounded", TIMER_START_UNBOUNDED_CYCLE_PROCESS_XML);
        seedStartTimer("proc-unbounded", LocalDateTime.now().minusMinutes(1), -1);

        for (int i = 1; i <= 3; i++) {
            processInstanceService.processDueStartTimers();
            assertEquals(i, instances.values().stream().filter(p -> "proc-unbounded".equals(p.getProcessId())).count());
            BpmnProcessStartTimer schedule = startTimers.get("proc-unbounded");
            assertNotNull(schedule);
            assertEquals(-1, schedule.getRepeatsRemaining());
            assertTrue(schedule.getNextFireAt().isAfter(LocalDateTime.now()));
            schedule.setNextFireAt(LocalDateTime.now().minusMinutes(1));
        }
    }

    @Test
    @DisplayName("Should remove a start-timer schedule and start nothing when its process was deleted")
    void testProcessDueStartTimersSkipsWhenProcessDeleted() {
        seedStartTimer("deleted-proc", LocalDateTime.now().minusMinutes(1), null);

        processInstanceService.processDueStartTimers();

        assertTrue(instances.isEmpty());
        assertNull(startTimers.get("deleted-proc"));
    }

    @Test
    @DisplayName("Should remove a stale start-timer schedule and start nothing when the live XML no longer has a timer")
    void testProcessDueStartTimersSkipsWhenXmlNoLongerHasTimer() {
        seedProcess("proc-1", SIMPLE_PROCESS_XML);
        seedStartTimer("proc-1", LocalDateTime.now().minusMinutes(1), null);

        processInstanceService.processDueStartTimers();

        assertTrue(instances.isEmpty());
        assertNull(startTimers.get("proc-1"));
    }

    @Test
    @DisplayName("Should run a service task's connector with resolved inputs and store its outputs as variables")
    void testStartInstanceRunsServiceTaskConnector() {
        seedProcess("proc-1", CONNECTOR_PROCESS_XML);
        connectorInvoker = (connectorId, inputs) -> Map.of("score", 720);

        ProcessInstanceResponse response = processInstanceService.startInstance(
                startRequest("proc-1", Map.of("customerId", "C-1")), "alice");

        assertEquals(1, connectorCalls.size());
        // Literal input passed through as written, ${...} input resolved from the process variables.
        assertEquals("https://api.example.com/credit", connectorCalls.get(0).get("url"));
        assertEquals("C-1", connectorCalls.get(0).get("customerId"));
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(720, response.getVariables().get("creditScore"));
        assertNull(response.getIncidentMessage());
    }

    @Test
    @DisplayName("Should park the instance in FAILED with an incident when its connector fails, instead of discarding it")
    void testStartInstanceRecordsIncidentWhenConnectorFails() {
        seedProcess("proc-1", CONNECTOR_PROCESS_XML);
        connectorInvoker = (connectorId, inputs) -> {
            throw new AppException("connect timed out", 502);
        };

        ProcessInstanceResponse response = processInstanceService.startInstance(
                startRequest("proc-1", Map.of("customerId", "C-1")), "alice");

        assertEquals("FAILED", response.getStatus());
        assertEquals("call1", response.getIncidentNodeId());
        assertEquals("call1", response.getCurrentNodeId());
        assertTrue(response.getIncidentMessage().contains("connect timed out"));
        assertNull(response.getCompletedAt());
        // The instance row survives the failure so it can be inspected and resumed.
        assertEquals(1, instances.size());
        assertEquals("FAILED", instances.values().iterator().next().getStatus());
    }
}
