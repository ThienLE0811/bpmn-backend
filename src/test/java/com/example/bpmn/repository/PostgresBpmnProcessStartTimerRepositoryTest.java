package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnProcessStartTimer;
import com.example.bpmn.repository.impl.PostgresBpmnProcessStartTimerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresBpmnProcessStartTimerRepositoryTest extends PostgresRepositoryTestBase {

    private final BpmnProcessStartTimerRepository repository = new PostgresBpmnProcessStartTimerRepository();

    @BeforeEach
    void insertParentProcesses() {
        insertProcess("p1");
        insertProcess("p2");
        insertProcess("p3");
    }

    private BpmnProcessStartTimer newTimer(String processId, LocalDateTime nextFireAt, Integer repeatsRemaining) {
        BpmnProcessStartTimer timer = new BpmnProcessStartTimer();
        timer.setProcessId(processId);
        timer.setNextFireAt(nextFireAt);
        timer.setRepeatsRemaining(repeatsRemaining);
        timer.setCreatedAt(NOW);
        timer.setUpdatedAt(NOW);
        return timer;
    }

    @Test
    @DisplayName("save then findByProcessId round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newTimer("p1", NOW.plusHours(1), 3));

        BpmnProcessStartTimer found = repository.findByProcessId("p1").orElseThrow();

        assertEquals("p1", found.getProcessId());
        assertEquals(NOW.plusHours(1), found.getNextFireAt());
        assertEquals(3, found.getRepeatsRemaining());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW, found.getUpdatedAt());
    }

    @Test
    @DisplayName("a one-shot schedule reads back with a null repeats counter, not 0")
    void oneShotScheduleKeepsNullRepeats() {
        repository.save(newTimer("p1", NOW.plusHours(1), null));

        assertNull(repository.findByProcessId("p1").orElseThrow().getRepeatsRemaining(),
                "getInt()/wasNull() must not turn SQL NULL into 0");
    }

    @Test
    @DisplayName("the unbounded-cycle marker -1 round-trips")
    void unboundedCycleMarkerRoundTrips() {
        repository.save(newTimer("p1", NOW.plusHours(1), -1));

        assertEquals(-1, repository.findByProcessId("p1").orElseThrow().getRepeatsRemaining());
    }

    @Test
    @DisplayName("re-saving the same process reschedules in place, keeping created_at")
    void saveUpsertsOnProcessId() {
        repository.save(newTimer("p1", NOW.plusHours(1), 3));

        BpmnProcessStartTimer rescheduled = newTimer("p1", NOW.plusHours(2), 2);
        rescheduled.setCreatedAt(NOW.plusHours(2));
        rescheduled.setUpdatedAt(NOW.plusHours(2));
        repository.save(rescheduled);

        assertEquals(1, repository.findDue(NOW.plusDays(1)).size(), "process_id is the primary key");
        BpmnProcessStartTimer found = repository.findByProcessId("p1").orElseThrow();
        assertEquals(NOW.plusHours(2), found.getNextFireAt());
        assertEquals(2, found.getRepeatsRemaining());
        assertEquals(NOW.plusHours(2), found.getUpdatedAt());
        // created_at is not in the DO UPDATE SET list.
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("an exhausted bounded cycle can be rescheduled back to a null counter")
    void repeatsCounterCanBeClearedOnReschedule() {
        repository.save(newTimer("p1", NOW.plusHours(1), 1));

        repository.save(newTimer("p1", NOW.plusHours(2), null));

        assertNull(repository.findByProcessId("p1").orElseThrow().getRepeatsRemaining());
    }

    @Test
    @DisplayName("findDue includes schedules due exactly now and excludes future ones")
    void findDueUsesInclusiveBoundary() {
        repository.save(newTimer("p1", NOW.minusMinutes(1), null));
        repository.save(newTimer("p2", NOW, null));
        repository.save(newTimer("p3", NOW.plusMinutes(1), null));

        List<String> processIds = repository.findDue(NOW).stream()
                .map(BpmnProcessStartTimer::getProcessId)
                .sorted()
                .toList();

        assertEquals(List.of("p1", "p2"), processIds);
    }

    @Test
    @DisplayName("deleteByProcessId removes the schedule and is a no-op for an unknown process")
    void deleteByProcessIdIsIdempotent() {
        repository.save(newTimer("p1", NOW.plusHours(1), null));

        repository.deleteByProcessId("p1");

        assertTrue(repository.findByProcessId("p1").isEmpty());
        assertDoesNotThrow(() -> repository.deleteByProcessId("p1"));
        assertDoesNotThrow(() -> repository.deleteByProcessId("never-existed"));
    }

    @Test
    @DisplayName("findByProcessId returns empty for a process with no schedule")
    void findByProcessIdReturnsEmptyWhenMissing() {
        assertTrue(repository.findByProcessId("p1").isEmpty());
    }
}
