package com.example.bpmn.repository;

import com.example.bpmn.model.ProcessInstanceTimer;
import com.example.bpmn.repository.impl.PostgresProcessInstanceTimerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresProcessInstanceTimerRepositoryTest extends PostgresRepositoryTestBase {

    private final ProcessInstanceTimerRepository repository = new PostgresProcessInstanceTimerRepository();

    @BeforeEach
    void insertParentRows() {
        insertProcess("p1");
        insertInstance("i1", "p1");
        insertInstance("i2", "p1");
    }

    private ProcessInstanceTimer newTimer(String id, String instanceId, LocalDateTime dueDate) {
        ProcessInstanceTimer timer = new ProcessInstanceTimer();
        timer.setId(id);
        timer.setProcessInstanceId(instanceId);
        timer.setNodeId("wait-" + id);
        timer.setDueDate(dueDate);
        timer.setCreatedAt(NOW);
        return timer;
    }

    @Test
    @DisplayName("save then findById round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newTimer("w1", "i1", NOW.plusHours(1)));

        ProcessInstanceTimer found = repository.findById("w1").orElseThrow();

        assertEquals("w1", found.getId());
        assertEquals("i1", found.getProcessInstanceId());
        assertEquals("wait-w1", found.getNodeId());
        assertEquals(NOW.plusHours(1), found.getDueDate());
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("save defaults created_at to now when the model leaves it null")
    void saveDefaultsCreatedAt() {
        ProcessInstanceTimer timer = newTimer("w1", "i1", NOW.plusHours(1));
        timer.setCreatedAt(null);
        LocalDateTime beforeSave = LocalDateTime.now().minusSeconds(1);

        repository.save(timer);

        assertTrue(repository.findById("w1").orElseThrow().getCreatedAt().isAfter(beforeSave));
    }

    @Test
    @DisplayName("re-saving the same id only moves the due date")
    void saveUpsertsDueDateOnly() {
        repository.save(newTimer("w1", "i1", NOW.plusHours(1)));

        ProcessInstanceTimer changed = newTimer("w1", "i1", NOW.plusHours(5));
        changed.setNodeId("some-other-node");
        changed.setCreatedAt(NOW.plusHours(5));
        repository.save(changed);

        assertEquals(1, repository.findByProcessInstanceId("i1").size());
        ProcessInstanceTimer found = repository.findById("w1").orElseThrow();
        assertEquals(NOW.plusHours(5), found.getDueDate());
        // node_id / created_at are not in the DO UPDATE SET list.
        assertEquals("wait-w1", found.getNodeId());
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("findByProcessInstanceId returns only that instance's waits")
    void findByProcessInstanceIdIsScoped() {
        repository.save(newTimer("w1", "i1", NOW.plusHours(1)));
        repository.save(newTimer("w2", "i1", NOW.plusHours(2)));
        repository.save(newTimer("other", "i2", NOW.plusHours(1)));

        List<String> ids = repository.findByProcessInstanceId("i1").stream()
                .map(ProcessInstanceTimer::getId)
                .sorted()
                .toList();

        assertEquals(List.of("w1", "w2"), ids);
        assertTrue(repository.findByProcessInstanceId("unknown").isEmpty());
    }

    @Test
    @DisplayName("findDueTimers includes waits due exactly now and excludes future ones")
    void findDueTimersUsesInclusiveBoundary() {
        repository.save(newTimer("past", "i1", NOW.minusMinutes(1)));
        repository.save(newTimer("exactly-now", "i1", NOW));
        repository.save(newTimer("future", "i1", NOW.plusMinutes(1)));

        List<String> ids = repository.findDueTimers(NOW).stream()
                .map(ProcessInstanceTimer::getId)
                .sorted()
                .toList();

        assertEquals(List.of("exactly-now", "past"), ids);
    }

    @Test
    @DisplayName("deleteById reports whether a row was removed")
    void deleteByIdReportsWhetherRowWasRemoved() {
        repository.save(newTimer("w1", "i1", NOW.plusHours(1)));

        assertTrue(repository.deleteById("w1"));
        assertFalse(repository.deleteById("w1"));
        assertTrue(repository.findById("w1").isEmpty());
    }
}
