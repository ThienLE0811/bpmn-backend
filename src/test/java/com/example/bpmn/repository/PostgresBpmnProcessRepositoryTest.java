package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.BpmnProcessStartTimer;
import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.repository.impl.PostgresBpmnProcessRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessStartTimerRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresBpmnProcessRepositoryTest extends PostgresRepositoryTestBase {

    private final BpmnProcessRepository repository = new PostgresBpmnProcessRepository();

    private BpmnProcess newProcess(String id, String processKey, Integer version, LocalDateTime createdAt) {
        BpmnProcess process = new BpmnProcess(id, processKey, "Process " + id, version, "<definitions id=\"" + id + "\"/>", "ACTIVE");
        process.setDescription("Description " + id);
        process.setCategory("CREDIT");
        process.setCreatedBy("creator");
        process.setUpdatedBy("creator");
        process.setCreatedAt(createdAt);
        process.setUpdatedAt(createdAt);
        return process;
    }

    @Test
    @DisplayName("save then findById round-trips every column, including name <-> process_name")
    void savePersistsAllColumns() {
        repository.save(newProcess("p1", "ORDER", 3, NOW));

        BpmnProcess found = repository.findById("p1").orElseThrow();

        assertEquals("p1", found.getId());
        assertEquals("ORDER", found.getProcessKey());
        // The model field is `name`, the column is `process_name`.
        assertEquals("Process p1", found.getName());
        assertEquals("Description p1", found.getDescription());
        assertEquals("CREDIT", found.getCategory());
        assertEquals(3, found.getVersion());
        assertEquals("<definitions id=\"p1\"/>", found.getBpmnXml());
        assertEquals("ACTIVE", found.getStatus());
        assertEquals("creator", found.getCreatedBy());
        assertEquals("creator", found.getUpdatedBy());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW, found.getUpdatedAt());
    }

    @Test
    @DisplayName("save writes version 1 when the model has no version")
    void saveDefaultsVersionToOne() {
        repository.save(newProcess("p1", "ORDER", null, NOW));

        assertEquals(1, repository.findById("p1").orElseThrow().getVersion());
    }

    @Test
    @DisplayName("save on an existing id updates in place, keeping created_at and created_by")
    void saveUpsertsExistingRow() {
        repository.save(newProcess("p1", "ORDER", 1, NOW));

        BpmnProcess changed = newProcess("p1", "ORDER_V2", 2, NOW.plusHours(1));
        changed.setName("Renamed");
        changed.setDescription("Changed");
        changed.setCategory("RISK");
        changed.setBpmnXml("<definitions id=\"changed\"/>");
        changed.setStatus("INACTIVE");
        changed.setCreatedBy("someone-else");
        changed.setUpdatedBy("editor");
        repository.save(changed);

        assertEquals(1, repository.count());
        BpmnProcess found = repository.findById("p1").orElseThrow();
        assertEquals("ORDER_V2", found.getProcessKey());
        assertEquals("Renamed", found.getName());
        assertEquals("Changed", found.getDescription());
        assertEquals("RISK", found.getCategory());
        assertEquals(2, found.getVersion());
        assertEquals("<definitions id=\"changed\"/>", found.getBpmnXml());
        assertEquals("INACTIVE", found.getStatus());
        assertEquals("editor", found.getUpdatedBy());
        // ON CONFLICT deliberately leaves the creation audit columns untouched.
        assertEquals("creator", found.getCreatedBy());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW.plusHours(1), found.getUpdatedAt());
    }

    @Test
    @DisplayName("save keeps nullable columns null")
    void savePersistsNullableColumnsAsNull() {
        BpmnProcess process = newProcess("p1", "ORDER", 1, NOW);
        process.setDescription(null);
        process.setCategory(null);
        process.setBpmnXml(null);
        process.setCreatedBy(null);
        process.setUpdatedBy(null);

        repository.save(process);

        BpmnProcess found = repository.findById("p1").orElseThrow();
        assertNull(found.getDescription());
        assertNull(found.getCategory());
        assertNull(found.getBpmnXml());
        assertNull(found.getCreatedBy());
        assertNull(found.getUpdatedBy());
    }

    @Test
    @DisplayName("findByProcessKey returns the highest version sharing that key")
    void findByProcessKeyReturnsHighestVersion() {
        repository.save(newProcess("p1", "ORDER", 1, NOW.minusDays(1)));
        repository.save(newProcess("p3", "ORDER", 3, NOW.minusDays(3)));
        repository.save(newProcess("p2", "ORDER", 2, NOW));
        repository.save(newProcess("other", "CLAIM", 9, NOW));

        BpmnProcess found = repository.findByProcessKey("ORDER").orElseThrow();

        assertEquals("p3", found.getId());
        assertEquals(3, found.getVersion());
    }

    @Test
    @DisplayName("findByProcessKey returns empty for an unknown key")
    void findByProcessKeyReturnsEmptyWhenMissing() {
        assertTrue(repository.findByProcessKey("NOPE").isEmpty());
    }

    @Test
    @DisplayName("findAll returns newest first, findPage pages over the same order")
    void findAllAndFindPageOrderByCreatedAtDesc() {
        repository.save(newProcess("a", "A", 1, NOW.minusDays(3)));
        repository.save(newProcess("b", "B", 1, NOW.minusDays(2)));
        repository.save(newProcess("c", "C", 1, NOW.minusDays(1)));

        assertEquals(List.of("c", "b", "a"), repository.findAll().stream().map(BpmnProcess::getId).toList());
        assertEquals(List.of("c", "b"), repository.findPage(2, 0).stream().map(BpmnProcess::getId).toList());
        assertEquals(List.of("a"), repository.findPage(2, 2).stream().map(BpmnProcess::getId).toList());
        assertEquals(3, repository.count());
    }

    @Test
    @DisplayName("deleteById reports whether a row was removed")
    void deleteByIdReportsWhetherRowWasRemoved() {
        repository.save(newProcess("p1", "ORDER", 1, NOW));

        assertTrue(repository.deleteById("p1"));
        assertFalse(repository.deleteById("p1"));
    }

    @Test
    @DisplayName("deleting a process cascades to its versions and its start-timer schedule")
    void deleteCascadesToVersionsAndStartTimer() {
        repository.save(newProcess("p1", "ORDER", 1, NOW));

        BpmnProcessVersionRepository versionRepository = new PostgresBpmnProcessVersionRepository();
        BpmnProcessVersion version = new BpmnProcessVersion();
        version.setId("v1");
        version.setProcessId("p1");
        version.setVersion(1);
        version.setBpmnXml("<definitions/>");
        version.setCreatedBy("creator");
        version.setCreatedAt(NOW);
        versionRepository.save(version);

        BpmnProcessStartTimerRepository startTimerRepository = new PostgresBpmnProcessStartTimerRepository();
        BpmnProcessStartTimer timer = new BpmnProcessStartTimer();
        timer.setProcessId("p1");
        timer.setNextFireAt(NOW.plusHours(1));
        timer.setCreatedAt(NOW);
        timer.setUpdatedAt(NOW);
        startTimerRepository.save(timer);

        assertTrue(repository.deleteById("p1"));

        assertTrue(versionRepository.findByProcessId("p1").isEmpty());
        assertTrue(startTimerRepository.findByProcessId("p1").isEmpty());
    }
}
