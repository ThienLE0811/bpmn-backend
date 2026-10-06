package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.repository.impl.PostgresBpmnProcessVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresBpmnProcessVersionRepositoryTest extends PostgresRepositoryTestBase {

    private final BpmnProcessVersionRepository repository = new PostgresBpmnProcessVersionRepository();

    @BeforeEach
    void insertParentProcesses() {
        insertProcess("p1");
        insertProcess("p2");
    }

    private BpmnProcessVersion newVersion(String id, String processId, int version, LocalDateTime createdAt) {
        BpmnProcessVersion snapshot = new BpmnProcessVersion();
        snapshot.setId(id);
        snapshot.setProcessId(processId);
        snapshot.setVersion(version);
        snapshot.setBpmnXml("<definitions id=\"" + id + "\"/>");
        snapshot.setCreatedBy("creator");
        snapshot.setCreatedAt(createdAt);
        return snapshot;
    }

    @Test
    @DisplayName("save then findByProcessIdAndVersion round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newVersion("v1", "p1", 2, NOW));

        BpmnProcessVersion found = repository.findByProcessIdAndVersion("p1", 2).orElseThrow();

        assertEquals("v1", found.getId());
        assertEquals("p1", found.getProcessId());
        assertEquals(2, found.getVersion());
        assertEquals("<definitions id=\"v1\"/>", found.getBpmnXml());
        assertEquals("creator", found.getCreatedBy());
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("re-saving the same (process_id, version) updates the snapshot but keeps the original row id")
    void saveUpsertsOnProcessIdAndVersion() {
        repository.save(newVersion("v1", "p1", 1, NOW));

        BpmnProcessVersion resaved = newVersion("v2-different-id", "p1", 1, NOW.plusHours(1));
        resaved.setCreatedBy("editor");
        repository.save(resaved);

        List<BpmnProcessVersion> all = repository.findByProcessId("p1");
        assertEquals(1, all.size(), "the conflict target is (process_id, version), so no second row");
        BpmnProcessVersion found = all.get(0);
        // The id column is not in the DO UPDATE SET list - the first write's id wins.
        assertEquals("v1", found.getId());
        assertEquals("<definitions id=\"v2-different-id\"/>", found.getBpmnXml());
        assertEquals("editor", found.getCreatedBy());
        assertEquals(NOW.plusHours(1), found.getCreatedAt());
    }

    @Test
    @DisplayName("findByProcessId returns only that process's snapshots, oldest version first")
    void findByProcessIdOrdersByVersionAsc() {
        repository.save(newVersion("v3", "p1", 3, NOW));
        repository.save(newVersion("v1", "p1", 1, NOW));
        repository.save(newVersion("v2", "p1", 2, NOW));
        repository.save(newVersion("other", "p2", 1, NOW));

        List<Integer> versions = repository.findByProcessId("p1").stream()
                .map(BpmnProcessVersion::getVersion)
                .toList();

        assertEquals(List.of(1, 2, 3), versions);
    }

    @Test
    @DisplayName("lookups of an unknown process or version return empty")
    void lookupsReturnEmptyWhenMissing() {
        repository.save(newVersion("v1", "p1", 1, NOW));

        assertTrue(repository.findByProcessId("unknown").isEmpty());
        assertTrue(repository.findByProcessIdAndVersion("p1", 99).isEmpty());
        assertTrue(repository.findByProcessIdAndVersion("p2", 1).isEmpty());
    }
}
