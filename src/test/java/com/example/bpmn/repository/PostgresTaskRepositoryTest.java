package com.example.bpmn.repository;

import com.example.bpmn.model.Task;
import com.example.bpmn.repository.impl.PostgresTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresTaskRepositoryTest extends PostgresRepositoryTestBase {

    private final TaskRepository repository = new PostgresTaskRepository();

    @BeforeEach
    void insertParentRows() {
        insertProcess("p1");
        insertProcess("p2");
        insertInstance("i1", "p1");
        insertInstance("i2", "p2");
    }

    private Task newTask(String id, String processId, String instanceId, String assigneeId,
                         String status, LocalDateTime createdAt) {
        Task task = new Task(id, processId, "Task " + id, "Description " + id, assigneeId, status);
        task.setProcessInstanceId(instanceId);
        task.setNodeId("node-" + id);
        task.setCreatedAt(createdAt);
        task.setUpdatedAt(createdAt);
        return task;
    }

    @Test
    @DisplayName("save then findById round-trips every column")
    void savePersistsAllColumns() {
        Task task = newTask("t1", "p1", "i1", "u1", "CLAIMED", NOW);
        task.setClaimedBy("u1");
        task.setClaimedAt(NOW.plusMinutes(5));
        task.setCompletedBy("u2");
        task.setCompletedAt(NOW.plusMinutes(10));
        task.setDueDate(NOW.plusHours(2));
        task.setTimerRepeatsRemaining(3);

        repository.save(task);

        Task found = repository.findById("t1").orElseThrow();
        assertEquals("t1", found.getId());
        assertEquals("p1", found.getProcessId());
        assertEquals("i1", found.getProcessInstanceId());
        assertEquals("node-t1", found.getNodeId());
        assertEquals("Task t1", found.getName());
        assertEquals("Description t1", found.getDescription());
        assertEquals("u1", found.getAssigneeId());
        assertEquals("CLAIMED", found.getStatus());
        assertEquals("u1", found.getClaimedBy());
        assertEquals(NOW.plusMinutes(5), found.getClaimedAt());
        assertEquals("u2", found.getCompletedBy());
        assertEquals(NOW.plusMinutes(10), found.getCompletedAt());
        assertEquals(NOW.plusHours(2), found.getDueDate());
        assertEquals(3, found.getTimerRepeatsRemaining());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW, found.getUpdatedAt());
    }

    @Test
    @DisplayName("an absent timer reads back as null, not as 0")
    void nullTimerColumnsReadBackAsNull() {
        repository.save(newTask("t1", "p1", "i1", "u1", "PENDING", NOW));

        Task found = repository.findById("t1").orElseThrow();
        assertNull(found.getDueDate());
        assertNull(found.getTimerRepeatsRemaining(), "getInt()/wasNull() must not turn SQL NULL into 0");
        assertNull(found.getClaimedAt());
        assertNull(found.getCompletedAt());
    }

    @Test
    @DisplayName("the unbounded-cycle marker -1 round-trips")
    void unboundedCycleMarkerRoundTrips() {
        Task task = newTask("t1", "p1", "i1", "u1", "PENDING", NOW);
        task.setDueDate(NOW.plusMinutes(10));
        task.setTimerRepeatsRemaining(-1);

        repository.save(task);

        assertEquals(-1, repository.findById("t1").orElseThrow().getTimerRepeatsRemaining());
    }

    @Test
    @DisplayName("save on an existing id updates in place, keeping the original created_at")
    void saveUpsertsExistingRow() {
        repository.save(newTask("t1", "p1", "i1", "u1", "PENDING", NOW));

        Task changed = newTask("t1", "p1", "i1", "u2", "COMPLETED", NOW);
        changed.setName("Renamed");
        changed.setCompletedBy("u2");
        changed.setCompletedAt(NOW.plusHours(1));
        changed.setTimerRepeatsRemaining(2);
        changed.setUpdatedAt(NOW.plusHours(1));
        repository.save(changed);

        assertEquals(1, repository.findAll().size());
        Task found = repository.findById("t1").orElseThrow();
        assertEquals("Renamed", found.getName());
        assertEquals("u2", found.getAssigneeId());
        assertEquals("COMPLETED", found.getStatus());
        assertEquals("u2", found.getCompletedBy());
        assertEquals(NOW.plusHours(1), found.getCompletedAt());
        assertEquals(2, found.getTimerRepeatsRemaining());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW.plusHours(1), found.getUpdatedAt());
    }

    @Test
    @DisplayName("clearing a timer on an existing task writes SQL NULL back")
    void saveClearsTimerColumns() {
        Task task = newTask("t1", "p1", "i1", "u1", "PENDING", NOW);
        task.setDueDate(NOW.plusMinutes(10));
        task.setTimerRepeatsRemaining(1);
        repository.save(task);

        task.setDueDate(null);
        task.setTimerRepeatsRemaining(null);
        repository.save(task);

        Task found = repository.findById("t1").orElseThrow();
        assertNull(found.getDueDate());
        assertNull(found.getTimerRepeatsRemaining());
    }

    @Test
    @DisplayName("findDueTimers returns only open tasks whose due date has passed")
    void findDueTimersFiltersByDueDateAndStatus() {
        repository.save(dueTask("pending-past", "PENDING", NOW.minusMinutes(1)));
        repository.save(dueTask("claimed-past", "CLAIMED", NOW.minusMinutes(1)));
        repository.save(dueTask("pending-exactly-now", "PENDING", NOW));
        repository.save(dueTask("pending-future", "PENDING", NOW.plusMinutes(1)));
        repository.save(dueTask("completed-past", "COMPLETED", NOW.minusMinutes(1)));
        repository.save(dueTask("cancelled-past", "CANCELLED", NOW.minusMinutes(1)));
        repository.save(newTask("no-due-date", "p1", "i1", "u1", "PENDING", NOW));

        List<String> ids = repository.findDueTimers(NOW).stream().map(Task::getId).sorted().toList();

        assertEquals(List.of("claimed-past", "pending-exactly-now", "pending-past"), ids);
    }

    private Task dueTask(String id, String status, LocalDateTime dueDate) {
        Task task = newTask(id, "p1", "i1", "u1", status, NOW);
        task.setDueDate(dueDate);
        return task;
    }

    @Test
    @DisplayName("findByProcessId returns only that process's tasks, oldest first")
    void findByProcessIdOrdersByCreatedAtAsc() {
        repository.save(newTask("t2", "p1", "i1", "u1", "PENDING", NOW.minusHours(1)));
        repository.save(newTask("t1", "p1", "i1", "u1", "PENDING", NOW.minusHours(2)));
        repository.save(newTask("other", "p2", "i2", "u1", "PENDING", NOW));

        assertEquals(List.of("t1", "t2"), repository.findByProcessId("p1").stream().map(Task::getId).toList());
        assertTrue(repository.findByProcessId("unknown").isEmpty());
    }

    @Test
    @DisplayName("findByAssigneeId returns only that user's tasks, oldest first")
    void findByAssigneeIdOrdersByCreatedAtAsc() {
        repository.save(newTask("t2", "p1", "i1", "u1", "PENDING", NOW.minusHours(1)));
        repository.save(newTask("t1", "p1", "i1", "u1", "PENDING", NOW.minusHours(2)));
        repository.save(newTask("other", "p1", "i1", "u2", "PENDING", NOW));

        assertEquals(List.of("t1", "t2"), repository.findByAssigneeId("u1").stream().map(Task::getId).toList());
        assertTrue(repository.findByAssigneeId("unknown").isEmpty());
    }

    @Test
    @DisplayName("findByProcessInstanceId returns only that instance's tasks, oldest first")
    void findByProcessInstanceIdOrdersByCreatedAtAsc() {
        repository.save(newTask("t2", "p1", "i1", "u1", "PENDING", NOW.minusHours(1)));
        repository.save(newTask("t1", "p1", "i1", "u1", "PENDING", NOW.minusHours(2)));
        repository.save(newTask("other", "p2", "i2", "u1", "PENDING", NOW));

        assertEquals(List.of("t1", "t2"),
                repository.findByProcessInstanceId("i1").stream().map(Task::getId).toList());
        assertTrue(repository.findByProcessInstanceId("unknown").isEmpty());
    }

    @Test
    @DisplayName("findAll returns newest first")
    void findAllOrdersByCreatedAtDesc() {
        repository.save(newTask("old", "p1", "i1", "u1", "PENDING", NOW.minusDays(2)));
        repository.save(newTask("new", "p1", "i1", "u1", "PENDING", NOW));
        repository.save(newTask("middle", "p1", "i1", "u1", "PENDING", NOW.minusDays(1)));

        assertEquals(List.of("new", "middle", "old"), repository.findAll().stream().map(Task::getId).toList());
    }

    @Test
    @DisplayName("deleteById reports whether a row was removed")
    void deleteByIdReportsWhetherRowWasRemoved() {
        repository.save(newTask("t1", "p1", "i1", "u1", "PENDING", NOW));

        assertTrue(repository.deleteById("t1"));
        assertFalse(repository.deleteById("t1"));
    }

    @Test
    @DisplayName("a task may exist without a process instance (nullable FK)")
    void taskWithoutProcessInstanceIsAllowed() {
        Task task = newTask("t1", "p1", null, "u1", "PENDING", NOW);

        repository.save(task);

        assertNull(repository.findById("t1").orElseThrow().getProcessInstanceId());
    }
}
