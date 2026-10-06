package com.example.bpmn.repository;

import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.model.DmnDecisionVersion;
import com.example.bpmn.repository.impl.PostgresDmnDecisionRepository;
import com.example.bpmn.repository.impl.PostgresDmnDecisionVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresDmnDecisionRepositoryTest extends PostgresRepositoryTestBase {

    private final DmnDecisionRepository repository = new PostgresDmnDecisionRepository();

    private DmnDecision newDecision(String id, String decisionKey, Integer version, LocalDateTime createdAt) {
        DmnDecision decision = new DmnDecision(id, decisionKey, "Decision " + id, version, "<definitions id=\"" + id + "\"/>", "ACTIVE");
        decision.setDescription("Description " + id);
        decision.setHitPolicy("UNIQUE");
        decision.setCategory("CREDIT");
        decision.setCreatedBy("creator");
        decision.setUpdatedBy("creator");
        decision.setCreatedAt(createdAt);
        decision.setUpdatedAt(createdAt);
        return decision;
    }

    @Test
    @DisplayName("save then findById round-trips every column")
    void savePersistsAllColumns() {
        repository.save(newDecision("d1", "RISK_SCORE", 3, NOW));

        DmnDecision found = repository.findById("d1").orElseThrow();

        assertEquals("d1", found.getId());
        assertEquals("RISK_SCORE", found.getDecisionKey());
        assertEquals("Decision d1", found.getName());
        assertEquals("Description d1", found.getDescription());
        assertEquals("UNIQUE", found.getHitPolicy());
        assertEquals("CREDIT", found.getCategory());
        assertEquals(3, found.getVersion());
        assertEquals("<definitions id=\"d1\"/>", found.getDmnXml());
        assertEquals("ACTIVE", found.getStatus());
        assertEquals("creator", found.getCreatedBy());
        assertEquals("creator", found.getUpdatedBy());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW, found.getUpdatedAt());
    }

    @Test
    @DisplayName("save writes version 1 when the model has no version")
    void saveDefaultsVersionToOne() {
        repository.save(newDecision("d1", "RISK_SCORE", null, NOW));

        assertEquals(1, repository.findById("d1").orElseThrow().getVersion());
    }

    @Test
    @DisplayName("save on an existing id updates in place, keeping created_at and created_by")
    void saveUpsertsExistingRow() {
        repository.save(newDecision("d1", "RISK_SCORE", 1, NOW));

        DmnDecision changed = newDecision("d1", "RISK_SCORE_V2", 2, NOW.plusHours(1));
        changed.setName("Renamed");
        changed.setHitPolicy("FIRST");
        changed.setDmnXml("<definitions id=\"changed\"/>");
        changed.setStatus("INACTIVE");
        changed.setCreatedBy("someone-else");
        changed.setUpdatedBy("editor");
        repository.save(changed);

        assertEquals(1, repository.count());
        DmnDecision found = repository.findById("d1").orElseThrow();
        assertEquals("RISK_SCORE_V2", found.getDecisionKey());
        assertEquals("Renamed", found.getName());
        assertEquals("FIRST", found.getHitPolicy());
        assertEquals(2, found.getVersion());
        assertEquals("<definitions id=\"changed\"/>", found.getDmnXml());
        assertEquals("INACTIVE", found.getStatus());
        assertEquals("editor", found.getUpdatedBy());
        assertEquals("creator", found.getCreatedBy());
        assertEquals(NOW, found.getCreatedAt());
        assertEquals(NOW.plusHours(1), found.getUpdatedAt());
    }

    @Test
    @DisplayName("save keeps nullable columns null")
    void savePersistsNullableColumnsAsNull() {
        DmnDecision decision = newDecision("d1", "RISK_SCORE", 1, NOW);
        decision.setDescription(null);
        decision.setHitPolicy(null);
        decision.setCategory(null);
        decision.setDmnXml(null);

        repository.save(decision);

        DmnDecision found = repository.findById("d1").orElseThrow();
        assertNull(found.getDescription());
        assertNull(found.getHitPolicy());
        assertNull(found.getCategory());
        assertNull(found.getDmnXml());
    }

    @Test
    @DisplayName("findByDecisionKey returns the highest version sharing that key")
    void findByDecisionKeyReturnsHighestVersion() {
        repository.save(newDecision("d1", "RISK_SCORE", 1, NOW));
        repository.save(newDecision("d3", "RISK_SCORE", 3, NOW.minusDays(3)));
        repository.save(newDecision("d2", "RISK_SCORE", 2, NOW));
        repository.save(newDecision("other", "PRICING", 9, NOW));

        DmnDecision found = repository.findByDecisionKey("RISK_SCORE").orElseThrow();

        assertEquals("d3", found.getId());
        assertEquals(3, found.getVersion());
        assertTrue(repository.findByDecisionKey("NOPE").isEmpty());
    }

    @Test
    @DisplayName("findAll returns newest first, findPage pages over the same order")
    void findAllAndFindPageOrderByCreatedAtDesc() {
        repository.save(newDecision("a", "A", 1, NOW.minusDays(3)));
        repository.save(newDecision("b", "B", 1, NOW.minusDays(2)));
        repository.save(newDecision("c", "C", 1, NOW.minusDays(1)));

        assertEquals(List.of("c", "b", "a"), repository.findAll().stream().map(DmnDecision::getId).toList());
        assertEquals(List.of("c", "b"), repository.findPage(2, 0).stream().map(DmnDecision::getId).toList());
        assertEquals(List.of("a"), repository.findPage(2, 2).stream().map(DmnDecision::getId).toList());
        assertEquals(3, repository.count());
    }

    @Test
    @DisplayName("deleteById reports whether a row was removed and cascades to its versions")
    void deleteByIdCascadesToVersions() {
        repository.save(newDecision("d1", "RISK_SCORE", 1, NOW));

        DmnDecisionVersionRepository versionRepository = new PostgresDmnDecisionVersionRepository();
        DmnDecisionVersion version = new DmnDecisionVersion();
        version.setId("v1");
        version.setDecisionId("d1");
        version.setVersion(1);
        version.setDmnXml("<definitions/>");
        version.setCreatedBy("creator");
        version.setCreatedAt(NOW);
        versionRepository.save(version);

        assertTrue(repository.deleteById("d1"));
        assertFalse(repository.deleteById("d1"));
        assertTrue(versionRepository.findByDecisionId("d1").isEmpty());
    }
}
