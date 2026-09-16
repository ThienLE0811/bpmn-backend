package com.example.bpmn;

import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.model.DmnDecisionVersion;
import com.example.bpmn.repository.DmnDecisionRepository;
import com.example.bpmn.repository.DmnDecisionVersionRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.impl.DmnDecisionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class DmnDecisionServiceTest {

    private DmnDecisionService dmnDecisionService;
    private final Map<String, DmnDecision> storage = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        storage.clear();
        DmnDecisionRepository mockRepo = new DmnDecisionRepository() {
            @Override
            public DmnDecision save(DmnDecision decision) {
                storage.put(decision.getId(), decision);
                return decision;
            }

            @Override
            public Optional<DmnDecision> findById(String id) {
                return Optional.ofNullable(storage.get(id));
            }

            @Override
            public Optional<DmnDecision> findByDecisionKey(String decisionKey) {
                return storage.values().stream()
                        .filter(d -> decisionKey.equals(d.getDecisionKey()))
                        .findFirst();
            }

            @Override
            public List<DmnDecision> findAll() {
                return new ArrayList<>(storage.values());
            }

            @Override
            public List<DmnDecision> findPage(int limit, int offset) {
                List<DmnDecision> all = findAll();
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

        DmnDecisionVersionRepository mockVersionRepo = new DmnDecisionVersionRepository() {
            private final List<DmnDecisionVersion> versions = new ArrayList<>();

            @Override
            public DmnDecisionVersion save(DmnDecisionVersion version) {
                versions.add(version);
                return version;
            }

            @Override
            public List<DmnDecisionVersion> findByDecisionId(String decisionId) {
                return versions.stream()
                        .filter(v -> decisionId.equals(v.getDecisionId()))
                        .toList();
            }

            @Override
            public Optional<DmnDecisionVersion> findByDecisionIdAndVersion(String decisionId, int version) {
                return versions.stream()
                        .filter(v -> decisionId.equals(v.getDecisionId()) && version == v.getVersion())
                        .findFirst();
            }
        };

        dmnDecisionService = new DmnDecisionServiceImpl(mockRepo, mockVersionRepo);
    }

    @Test
    @DisplayName("Should return all dmn decisions with hitPolicy")
    void testGetAllDecisions() {
        DmnDecision d1 = new DmnDecision("1", "loan_scoring", "Loan Scoring Decision", "Description 1", "UNIQUE", "FINANCE", 1, "<dmn/>", "ACTIVE", "admin", "admin", null, null);
        DmnDecision d2 = new DmnDecision("2", "interest_rate", "Interest Rate Decision", "Description 2", "FIRST", "FINANCE", 1, "<dmn/>", "ACTIVE", "admin", "admin", null, null);
        storage.put("1", d1);
        storage.put("2", d2);

        List<DmnDecisionResponse> result = dmnDecisionService.getAllDecisions(1, 20).getContent();

        assertEquals(2, result.size());
        assertEquals("UNIQUE", result.get(0).getHitPolicy());
        assertEquals("FIRST", result.get(1).getHitPolicy());
    }

    @Test
    @DisplayName("Should get decision by id")
    void testGetDecisionById() {
        DmnDecision d = new DmnDecision("dmn-1", "credit_limit", "Credit Limit Calculation", "Calculate limit", "RULE ORDER", "CREDIT", 1, "<dmn/>", "ACTIVE", "admin", "admin", null, null);
        storage.put("dmn-1", d);

        DmnDecisionResponse response = dmnDecisionService.getDecisionById("dmn-1");

        assertNotNull(response);
        assertEquals("dmn-1", response.getId());
        assertEquals("credit_limit", response.getDecisionKey());
        assertEquals("Credit Limit Calculation", response.getName());
        assertEquals("RULE ORDER", response.getHitPolicy());
    }

    @Test
    @DisplayName("Should throw exception if decision not found by id")
    void testGetDecisionByIdNotFound() {
        AppException ex = assertThrows(AppException.class, () -> dmnDecisionService.getDecisionById("unknown"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should get decision by decisionKey")
    void testGetDecisionByKey() {
        DmnDecision d = new DmnDecision("dmn-1", "credit_limit", "Credit Limit Calculation", "Calculate limit", "COLLECT", "CREDIT", 1, "<dmn/>", "ACTIVE", "admin", "admin", null, null);
        storage.put("dmn-1", d);

        DmnDecisionResponse response = dmnDecisionService.getDecisionByKey("credit_limit");

        assertNotNull(response);
        assertEquals("credit_limit", response.getDecisionKey());
        assertEquals("COLLECT", response.getHitPolicy());
    }

    @Test
    @DisplayName("Should evaluate a DMN decision table and return its output columns")
    void testEvaluateDecision() {
        String dmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs">
                  <decision id="Decision_1" name="Risk">
                    <decisionTable id="DecisionTable_1" hitPolicy="UNIQUE">
                      <input id="Input_1">
                        <inputExpression id="InputExpression_1" typeRef="string">
                          <text>amount</text>
                        </inputExpression>
                      </input>
                      <output id="Output_1" name="riskLevel" typeRef="string" />
                      <rule id="Rule_1">
                        <inputEntry id="UnaryTests_1"><text>&gt;= 1000</text></inputEntry>
                        <outputEntry id="LiteralExpression_1"><text>"HIGH"</text></outputEntry>
                      </rule>
                      <rule id="Rule_2">
                        <inputEntry id="UnaryTests_2"><text>&lt; 1000</text></inputEntry>
                        <outputEntry id="LiteralExpression_2"><text>"LOW"</text></outputEntry>
                      </rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """;
        DmnDecision d = new DmnDecision("dmn-1", "risk_decision", "Risk Decision", 1, dmnXml, "ACTIVE");
        storage.put("dmn-1", d);

        Map<String, Object> result = dmnDecisionService.evaluate("risk_decision", Map.of("amount", 1500));

        assertEquals("HIGH", result.get("riskLevel"));
    }

    @Test
    @DisplayName("Should throw 404 when evaluating an unknown decision key")
    void testEvaluateDecisionNotFound() {
        AppException ex = assertThrows(AppException.class, () -> dmnDecisionService.evaluate("unknown", Map.of()));
        assertEquals(404, ex.getStatusCode());
    }
}
