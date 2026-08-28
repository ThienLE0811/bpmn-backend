package com.example.bpmn;

import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.repository.DmnDecisionRepository;
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
            public boolean deleteById(String id) {
                return storage.remove(id) != null;
            }
        };

        dmnDecisionService = new DmnDecisionServiceImpl(mockRepo);
    }

    @Test
    @DisplayName("Should return all dmn decisions with hitPolicy")
    void testGetAllDecisions() {
        DmnDecision d1 = new DmnDecision("1", "loan_scoring", "Loan Scoring Decision", "Description 1", "UNIQUE", "FINANCE", 1, "<dmn/>", "ACTIVE", "admin", "admin", null, null);
        DmnDecision d2 = new DmnDecision("2", "interest_rate", "Interest Rate Decision", "Description 2", "FIRST", "FINANCE", 1, "<dmn/>", "ACTIVE", "admin", "admin", null, null);
        storage.put("1", d1);
        storage.put("2", d2);

        List<DmnDecisionResponse> result = dmnDecisionService.getAllDecisions();

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
}
