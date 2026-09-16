package com.example.bpmn.dmn;

import com.example.bpmn.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DmnEvaluatorTest {

    private static final String[] LOW_RULE = {"< 1000", "true"};
    private static final String[] HIGH_RULE = {">= 1000", "false"};
    private static final String[] OVERLAPPING_RULE = {"< 2000", "false"};

    @Test
    @DisplayName("UNIQUE hit policy should return the single matching rule's output")
    void uniqueHitPolicyReturnsSingleMatch() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("UNIQUE", LOW_RULE, HIGH_RULE));

        Map<String, Object> low = DmnEvaluator.evaluate(table, Map.of("amount", 500));
        assertEquals(Boolean.TRUE, low.get("result"));

        Map<String, Object> high = DmnEvaluator.evaluate(table, Map.of("amount", 1500));
        assertEquals(Boolean.FALSE, high.get("result"));
    }

    @Test
    @DisplayName("UNIQUE hit policy should return an empty result when no rule matches")
    void uniqueHitPolicyReturnsEmptyWhenNoRuleMatches() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("UNIQUE", new String[]{"1000", "true"}));

        Map<String, Object> result = DmnEvaluator.evaluate(table, Map.of("amount", 999));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("UNIQUE hit policy should reject more than one matching rule")
    void uniqueHitPolicyRejectsMultipleMatches() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("UNIQUE", LOW_RULE, OVERLAPPING_RULE));

        AppException ex = assertThrows(AppException.class, () -> DmnEvaluator.evaluate(table, Map.of("amount", 500)));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("FIRST hit policy should return the first matching rule and ignore later matches")
    void firstHitPolicyReturnsFirstMatch() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("FIRST", LOW_RULE, OVERLAPPING_RULE));

        Map<String, Object> result = DmnEvaluator.evaluate(table, Map.of("amount", 500));

        assertEquals(Boolean.TRUE, result.get("result"));
    }

    @Test
    @DisplayName("The \"-\" wildcard should match any input value")
    void wildcardMatchesAnyValue() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("UNIQUE", new String[]{"-", "matched"}));

        Map<String, Object> result = DmnEvaluator.evaluate(table, Map.of("amount", 999999));

        assertEquals("matched", result.get("result"));
    }

    @Test
    @DisplayName("Unsupported hit policies should raise a clear error")
    void unsupportedHitPolicyThrows() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("PRIORITY", new String[]{"-", "true"}));

        AppException ex = assertThrows(AppException.class, () -> DmnEvaluator.evaluate(table, Map.of("amount", 1)));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("FEEL ranges should raise a clear v1-unsupported error rather than evaluate incorrectly")
    void rangeInputEntryThrows() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("UNIQUE", new String[]{"[100..200]", "true"}));

        AppException ex = assertThrows(AppException.class, () -> DmnEvaluator.evaluate(table, Map.of("amount", 150)));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("FEEL comma-separated value lists should raise a clear v1-unsupported error")
    void commaListInputEntryThrows() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.singleInputTable("UNIQUE", new String[]{"\"A\",\"B\"", "true"}));

        AppException ex = assertThrows(AppException.class, () -> DmnEvaluator.evaluate(table, Map.of("amount", "A")));
        assertEquals(400, ex.getStatusCode());
    }
}
