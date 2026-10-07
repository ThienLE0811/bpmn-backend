package com.example.bpmn.repository;

import com.example.bpmn.model.ProcessInstance;
import com.example.bpmn.model.ProcessInstanceTimer;
import com.example.bpmn.model.Task;
import com.example.bpmn.repository.impl.PostgresProcessInstanceRepository;
import com.example.bpmn.repository.impl.PostgresProcessInstanceTimerRepository;
import com.example.bpmn.repository.impl.PostgresTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresProcessInstanceRepositoryTest extends PostgresRepositoryTestBase {

    private final ProcessInstanceRepository repository = new PostgresProcessInstanceRepository();

    @BeforeEach
    void insertParentProcess() {
        insertProcess("p1");
    }

    private ProcessInstance newInstance(String id, LocalDateTime createdAt) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(id);
        instance.setProcessId("p1");
        instance.setProcessVersion(2);
        instance.setStatus("RUNNING");
        instance.setCurrentNodeId("task1");
        instance.setVariables(Map.of("amount", 1500, "approved", true, "customer", "ACME"));
        instance.setPendingJoinArrivals(Set.of("flow3", "flow4"));
        instance.setStartedBy("tester");
        instance.setStartedAt(createdAt);
        instance.setCreatedAt(createdAt);
        instance.setUpdatedAt(createdAt);
        return instance;
    }

    @Test
    @DisplayName("save then findById round-trips every column, including the JSON-encoded variables")
    void savePersistsAllColumns() {
        repository.save(newInstance("i1", NOW));

        ProcessInstance found = repository.findById("i1").orElseThrow();

        assertEquals("i1", found.getId());
        assertEquals("p1", found.getProcessId());
        assertEquals(2, found.getProcessVersion());
        assertEquals("RUNNING", found.getStatus());
        assertEquals("task1", found.getCurrentNodeId());
        assertEquals(Map.of("amount", 1500, "approved", true, "customer", "ACME"), found.getVariables());
        assertEquals(Set.of("flow3", "flow4"), found.getPendingJoinArrivals());
        assertEquals("tester", found.getStartedBy());
        assertEquals(NOW, found.getStartedAt());
        assertNull(found.getCompletedAt());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW, found.getUpdatedAt());
        assertNull(found.getIncidentNodeId());
        assertNull(found.getIncidentMessage());
    }

    @Test
    @DisplayName("save persists an incident, and a later successful save clears it again")
    void incidentIsPersistedAndCleared() {
        ProcessInstance instance = newInstance("i1", NOW);
        instance.markFailed("call1", "Connector \"http\" on service task call1 failed: connect timed out", NOW);

        repository.save(instance);

        ProcessInstance failed = repository.findById("i1").orElseThrow();
        assertEquals("FAILED", failed.getStatus());
        assertEquals("call1", failed.getIncidentNodeId());
        assertTrue(failed.getIncidentMessage().contains("connect timed out"));

        failed.clearIncident();
        failed.setStatus("RUNNING");
        repository.save(failed);

        ProcessInstance resumed = repository.findById("i1").orElseThrow();
        assertEquals("RUNNING", resumed.getStatus());
        assertNull(resumed.getIncidentNodeId());
        assertNull(resumed.getIncidentMessage());
    }

    @Test
    @DisplayName("null variables / pendingJoinArrivals read back as empty collections, not null")
    void nullCollectionsReadBackEmpty() {
        ProcessInstance instance = newInstance("i1", NOW);
        instance.setVariables(null);
        instance.setPendingJoinArrivals(null);

        repository.save(instance);

        ProcessInstance found = repository.findById("i1").orElseThrow();
        assertEquals(Map.of(), found.getVariables());
        assertEquals(Set.of(), found.getPendingJoinArrivals());
    }

    @Test
    @DisplayName("nested variable structures survive the JSON round trip")
    void nestedVariablesRoundTrip() {
        ProcessInstance instance = newInstance("i1", NOW);
        instance.setVariables(Map.of(
                "applicant", Map.of("name", "Lê", "score", 720),
                "documents", List.of("cmnd", "payslip")));

        repository.save(instance);

        ProcessInstance found = repository.findById("i1").orElseThrow();
        assertEquals(Map.of("name", "Lê", "score", 720), found.getVariables().get("applicant"));
        assertEquals(List.of("cmnd", "payslip"), found.getVariables().get("documents"));
    }

    @Test
    @DisplayName("save on an existing id updates the mutable state only, leaving the start audit intact")
    void saveUpsertsMutableStateOnly() {
        repository.save(newInstance("i1", NOW));

        ProcessInstance changed = newInstance("i1", NOW);
        changed.setProcessVersion(99);
        changed.setStatus("COMPLETED");
        changed.setCurrentNodeId("end1");
        changed.setVariables(Map.of("amount", 2000));
        changed.setPendingJoinArrivals(Set.of());
        changed.setStartedBy("someone-else");
        changed.setStartedAt(NOW.plusDays(1));
        changed.setCompletedAt(NOW.plusHours(2));
        changed.setUpdatedAt(NOW.plusHours(2));
        repository.save(changed);

        assertEquals(1, repository.count());
        ProcessInstance found = repository.findById("i1").orElseThrow();
        assertEquals("COMPLETED", found.getStatus());
        assertEquals("end1", found.getCurrentNodeId());
        assertEquals(Map.of("amount", 2000), found.getVariables());
        assertEquals(Set.of(), found.getPendingJoinArrivals());
        assertEquals(NOW.plusHours(2), found.getCompletedAt());
        assertEquals(NOW.plusHours(2), found.getUpdatedAt());
        // ON CONFLICT deliberately leaves the start audit columns untouched.
        assertEquals(2, found.getProcessVersion());
        assertEquals("tester", found.getStartedBy());
        assertEquals(NOW, found.getStartedAt());
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("findAll returns newest first, findPage pages over the same order")
    void findAllAndFindPageOrderByCreatedAtDesc() {
        repository.save(newInstance("a", NOW.minusDays(3)));
        repository.save(newInstance("b", NOW.minusDays(2)));
        repository.save(newInstance("c", NOW.minusDays(1)));

        assertEquals(List.of("c", "b", "a"), repository.findAll().stream().map(ProcessInstance::getId).toList());
        assertEquals(List.of("c", "b"), repository.findPage(2, 0).stream().map(ProcessInstance::getId).toList());
        assertEquals(List.of("a"), repository.findPage(2, 2).stream().map(ProcessInstance::getId).toList());
        assertEquals(3, repository.count());
    }

    @Test
    @DisplayName("findById returns empty for an unknown id")
    void findByIdReturnsEmptyWhenMissing() {
        assertTrue(repository.findById("nope").isEmpty());
    }

    @Test
    @DisplayName("deleting an instance cascades to its tasks and timer waits")
    void deleteCascadesToTasksAndTimerWaits() {
        repository.save(newInstance("i1", NOW));

        TaskRepository taskRepository = new PostgresTaskRepository();
        Task task = new Task("t1", "p1", "Review", "desc", "u1", "PENDING");
        task.setProcessInstanceId("i1");
        task.setNodeId("task1");
        task.setCreatedAt(NOW);
        task.setUpdatedAt(NOW);
        taskRepository.save(task);

        ProcessInstanceTimerRepository timerRepository = new PostgresProcessInstanceTimerRepository();
        ProcessInstanceTimer timer = new ProcessInstanceTimer();
        timer.setId("w1");
        timer.setProcessInstanceId("i1");
        timer.setNodeId("wait1");
        timer.setDueDate(NOW.plusHours(1));
        timer.setCreatedAt(NOW);
        timerRepository.save(timer);

        assertTrue(repository.deleteById("i1"));
        assertFalse(repository.deleteById("i1"));

        assertTrue(taskRepository.findById("t1").isEmpty());
        assertTrue(timerRepository.findById("w1").isEmpty());
    }
}
