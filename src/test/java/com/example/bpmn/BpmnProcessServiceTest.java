package com.example.bpmn;

import com.example.bpmn.dto.BpmnProcessRequest;
import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.dto.BpmnProcessUpdateRequest;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.BpmnProcessStartTimer;
import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.BpmnProcessStartTimerRepository;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.service.impl.BpmnProcessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class BpmnProcessServiceTest {

    private BpmnProcessService bpmnProcessService;
    private final Map<String, BpmnProcess> storage = new ConcurrentHashMap<>();
    private final Map<String, BpmnProcessStartTimer> startTimers = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        storage.clear();
        startTimers.clear();
        BpmnProcessRepository mockRepo = new BpmnProcessRepository() {
            @Override
            public BpmnProcess save(BpmnProcess process) {
                storage.put(process.getId(), process);
                return process;
            }

            @Override
            public Optional<BpmnProcess> findById(String id) {
                return Optional.ofNullable(storage.get(id));
            }

            @Override
            public Optional<BpmnProcess> findByProcessKey(String processKey) {
                return storage.values().stream()
                        .filter(p -> processKey.equals(p.getProcessKey()))
                        .findFirst();
            }

            @Override
            public List<BpmnProcess> findAll() {
                return new ArrayList<>(storage.values());
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
                return storage.size();
            }

            @Override
            public boolean deleteById(String id) {
                return storage.remove(id) != null;
            }
        };

        BpmnProcessVersionRepository mockVersionRepo = new BpmnProcessVersionRepository() {
            private final List<BpmnProcessVersion> versions = new ArrayList<>();

            @Override
            public BpmnProcessVersion save(BpmnProcessVersion version) {
                versions.add(version);
                return version;
            }

            @Override
            public List<BpmnProcessVersion> findByProcessId(String processId) {
                return versions.stream()
                        .filter(v -> processId.equals(v.getProcessId()))
                        .toList();
            }

            @Override
            public Optional<BpmnProcessVersion> findByProcessIdAndVersion(String processId, int version) {
                return versions.stream()
                        .filter(v -> processId.equals(v.getProcessId()) && version == v.getVersion())
                        .findFirst();
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
                return startTimers.values().stream()
                        .filter(t -> !t.getNextFireAt().isAfter(now))
                        .toList();
            }

            @Override
            public void deleteByProcessId(String processId) {
                startTimers.remove(processId);
            }
        };

        bpmnProcessService = new BpmnProcessServiceImpl(mockRepo, mockVersionRepo, mockStartTimerRepo);
    }

    @Test
    @DisplayName("Should return all bpmn processes")
    void testGetAllProcesses() {
        BpmnProcess p1 = new BpmnProcess("1", "loan_approval", "Loan Approval", 1, "<xml/>", "ACTIVE");
        BpmnProcess p2 = new BpmnProcess("2", "credit_card", "Credit Card Application", 1, "<xml/>", "ACTIVE");
        storage.put("1", p1);
        storage.put("2", p2);

        List<BpmnProcessResponse> result = bpmnProcessService.getAllProcesses(1, 20).getContent();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should get process by id")
    void testGetProcessById() {
        BpmnProcess p = new BpmnProcess("proc-1", "kyc_process", "KYC Process", 1, "<xml/>", "ACTIVE");
        storage.put("proc-1", p);

        BpmnProcessResponse response = bpmnProcessService.getProcessById("proc-1");

        assertNotNull(response);
        assertEquals("proc-1", response.getId());
        assertEquals("kyc_process", response.getProcessKey());
        assertEquals("KYC Process", response.getName());
    }

    @Test
    @DisplayName("Should throw exception if process not found by id")
    void testGetProcessByIdNotFound() {
        AppException ex = assertThrows(AppException.class, () -> bpmnProcessService.getProcessById("unknown"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should get process by processKey")
    void testGetProcessByKey() {
        BpmnProcess p = new BpmnProcess("proc-1", "kyc_process", "KYC Process", 1, "<xml/>", "ACTIVE");
        storage.put("proc-1", p);

        BpmnProcessResponse response = bpmnProcessService.getProcessByKey("kyc_process");

        assertNotNull(response);
        assertEquals("kyc_process", response.getProcessKey());
    }

    private static final String PLAIN_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs">
              <process id="p1">
                <startEvent id="start1" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" />
              </process>
            </definitions>
            """;

    private static final String TIMER_START_DATE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs">
              <process id="p1">
                <startEvent id="start1">
                  <timerEventDefinition><timeDate>2030-01-01T00:00:00</timeDate></timerEventDefinition>
                </startEvent>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" />
              </process>
            </definitions>
            """;

    private static final String TIMER_START_BOUNDED_CYCLE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs">
              <process id="p1">
                <startEvent id="start1">
                  <timerEventDefinition><timeCycle>R3/PT10M</timeCycle></timerEventDefinition>
                </startEvent>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="end1" />
                <endEvent id="end1" />
              </process>
            </definitions>
            """;

    @Test
    @DisplayName("Should create a start-timer schedule when creating a process with a timer start event")
    void testCreateProcessWithTimerStartEventCreatesSchedule() {
        BpmnProcessResponse created = bpmnProcessService.createProcess(
                new BpmnProcessRequest("timer_proc", "Timer Process", null, null, TIMER_START_DATE_PROCESS_XML), "alice");

        BpmnProcessStartTimer schedule = startTimers.get(created.getId());
        assertNotNull(schedule);
        assertEquals(LocalDateTime.parse("2030-01-01T00:00:00"), schedule.getNextFireAt());
        assertNull(schedule.getRepeatsRemaining());
    }

    @Test
    @DisplayName("Should not create a start-timer schedule for a plain start event")
    void testCreateProcessWithoutTimerDoesNotCreateSchedule() {
        BpmnProcessResponse created = bpmnProcessService.createProcess(
                new BpmnProcessRequest("plain_proc", "Plain Process", null, null, PLAIN_PROCESS_XML), "alice");

        assertNull(startTimers.get(created.getId()));
    }

    @Test
    @DisplayName("Should set repeatsRemaining for a bounded timeCycle start event")
    void testCreateProcessWithBoundedCycleStartEventSetsRepeatsRemaining() {
        BpmnProcessResponse created = bpmnProcessService.createProcess(
                new BpmnProcessRequest("cycle_proc", "Cycle Process", null, null, TIMER_START_BOUNDED_CYCLE_PROCESS_XML), "alice");

        assertEquals(3, startTimers.get(created.getId()).getRepeatsRemaining());
    }

    @Test
    @DisplayName("Should create a start-timer schedule when an update adds a timer to the start event")
    void testUpdateProcessAddingTimerCreatesSchedule() {
        BpmnProcessResponse created = bpmnProcessService.createProcess(
                new BpmnProcessRequest("proc", "Process", null, null, PLAIN_PROCESS_XML), "alice");
        assertNull(startTimers.get(created.getId()));

        BpmnProcessUpdateRequest update = new BpmnProcessUpdateRequest();
        update.setBpmnXml(TIMER_START_DATE_PROCESS_XML);
        bpmnProcessService.updateProcess(created.getId(), update, "alice", "USER");

        assertNotNull(startTimers.get(created.getId()));
    }

    @Test
    @DisplayName("Should delete the start-timer schedule when an update removes the timer from the start event")
    void testUpdateProcessRemovingTimerDeletesSchedule() {
        BpmnProcessResponse created = bpmnProcessService.createProcess(
                new BpmnProcessRequest("proc", "Process", null, null, TIMER_START_DATE_PROCESS_XML), "alice");
        assertNotNull(startTimers.get(created.getId()));

        BpmnProcessUpdateRequest update = new BpmnProcessUpdateRequest();
        update.setBpmnXml(PLAIN_PROCESS_XML);
        bpmnProcessService.updateProcess(created.getId(), update, "alice", "USER");

        assertNull(startTimers.get(created.getId()));
    }

    @Test
    @DisplayName("Should leave the start-timer schedule untouched when an update doesn't change the XML")
    void testUpdateProcessWithoutXmlChangeLeavesScheduleUntouched() {
        BpmnProcessResponse created = bpmnProcessService.createProcess(
                new BpmnProcessRequest("proc", "Process", null, null, TIMER_START_DATE_PROCESS_XML), "alice");
        BpmnProcessStartTimer before = startTimers.get(created.getId());

        BpmnProcessUpdateRequest update = new BpmnProcessUpdateRequest();
        update.setName("Renamed");
        bpmnProcessService.updateProcess(created.getId(), update, "alice", "USER");

        assertSame(before, startTimers.get(created.getId()));
    }
}
