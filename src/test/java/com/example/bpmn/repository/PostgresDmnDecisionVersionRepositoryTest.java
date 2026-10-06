package com.example.bpmn.repository;

import com.example.bpmn.model.DmnDecisionVersion;
import com.example.bpmn.repository.impl.PostgresDmnDecisionVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresDmnDecisionVersionRepositoryTest extends PostgresRepositoryTestBase {

    private final DmnDecisionVersionRepository repository = new PostgresDmnDecisionVersionRepository();

    @BeforeEach
    void insertParentDecisions() {
        insertDecision("d1");
        insertDecision("d2");
    }

    private DmnDecisionVersion newVersion(String id, String decisionId, int version, LocalDateTime createdAt) {
        DmnDecisionVersion snapshot = new DmnDecisionVersion();
        snapshot.setId(id);
        snapshot.setDecisionId(decisionId);
        snapshot.setVersion(version);
        snapshot.setDmnXml("<definitions id=\"" + id + "\"/>");
        snapshot.setCreatedBy("creator");
        snapshot.setCreatedAt(createdAt);
        return snapshot;
    }

    @Test
    @DisplayName("save then findByDecisionIdAndVersion round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newVersion("v1", "d1", 2, NOW));

        DmnDecisionVersion found = repository.findByDecisionIdAndVersion("d1", 2).orElseThrow();

        assertEquals("v1", found.getId());
        assertEquals("d1", found.getDecisionId());
        assertEquals(2, found.getVersion());
        assertEquals("<definitions id=\"v1\"/>", found.getDmnXml());
        assertEquals("creator", found.getCreatedBy());
        assertEquals(NOW, found.getCreatedAt());
    }

    @Test
    @DisplayName("re-saving the same (decision_id, version) updates the snapshot but keeps the original row id")
    void saveUpsertsOnDecisionIdAndVersion() {
        repository.save(newVersion("v1", "d1", 1, NOW));

        DmnDecisionVersion resaved = newVersion("v2-different-id", "d1", 1, NOW.plusHours(1));
        resaved.setCreatedBy("editor");
        repository.save(resaved);

        List<DmnDecisionVersion> all = repository.findByDecisionId("d1");
        assertEquals(1, all.size(), "the conflict target is (decision_id, version), so no second row");
        DmnDecisionVersion found = all.get(0);
        assertEquals("v1", found.getId());
        assertEquals("<definitions id=\"v2-different-id\"/>", found.getDmnXml());
        assertEquals("editor", found.getCreatedBy());
        assertEquals(NOW.plusHours(1), found.getCreatedAt());
    }

    @Test
    @DisplayName("findByDecisionId returns only that decision's snapshots, oldest version first")
    void findByDecisionIdOrdersByVersionAsc() {
        repository.save(newVersion("v3", "d1", 3, NOW));
        repository.save(newVersion("v1", "d1", 1, NOW));
        repository.save(newVersion("v2", "d1", 2, NOW));
        repository.save(newVersion("other", "d2", 1, NOW));

        List<Integer> versions = repository.findByDecisionId("d1").stream()
                .map(DmnDecisionVersion::getVersion)
                .toList();

        assertEquals(List.of(1, 2, 3), versions);
    }

    @Test
    @DisplayName("lookups of an unknown decision or version return empty")
    void lookupsReturnEmptyWhenMissing() {
        repository.save(newVersion("v1", "d1", 1, NOW));

        assertTrue(repository.findByDecisionId("unknown").isEmpty());
        assertTrue(repository.findByDecisionIdAndVersion("d1", 99).isEmpty());
        assertTrue(repository.findByDecisionIdAndVersion("d2", 1).isEmpty());
    }
}
