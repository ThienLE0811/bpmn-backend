package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcessEngineTest {

    private BpmnProcessDefinition definition;

    @BeforeEach
    void setUp() {
        definition = BpmnGraphParser.parse(BpmnFixtures.APPROVAL_PROCESS_XML);
    }

    @Test
    @DisplayName("Should stop at the first user task when advancing from the start event")
    void advanceFromStartStopsAtFirstUserTask() {
        AdvanceResult result = ProcessEngine.advance(definition, definition.getStartNodeId(), Map.of());

        assertFalse(result.isFullyResolved());
        assertEquals(List.of("task1"), result.getNewUserTaskNodeIds());
    }

    @Test
    @DisplayName("Should take the conditioned gateway branch when its expression is true")
    void gatewayTakesConditionedBranchWhenTrue() {
        AdvanceResult result = ProcessEngine.advance(definition, "task1", Map.of("amount", 5000));

        assertFalse(result.isFullyResolved());
        assertEquals(List.of("task2"), result.getNewUserTaskNodeIds());
    }

    @Test
    @DisplayName("Should fall back to the default gateway branch when the condition is false")
    void gatewayFallsBackToDefaultBranchWhenConditionFalse() {
        AdvanceResult result = ProcessEngine.advance(definition, "task1", Map.of("amount", 100));

        assertTrue(result.isFullyResolved());
    }

    @Test
    @DisplayName("Should complete the instance after the second user task")
    void advanceFromSecondUserTaskReachesEnd() {
        AdvanceResult result = ProcessEngine.advance(definition, "task2", Map.of());

        assertTrue(result.isFullyResolved());
    }

    @Test
    @DisplayName("Parallel gateway should fork into both branches in a single advance call")
    void parallelGatewayForksIntoBothBranches() {
        BpmnProcessDefinition parallelDef = BpmnGraphParser.parse(BpmnFixtures.PARALLEL_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(parallelDef, "task1", Map.of());

        assertFalse(result.isFullyResolved());
        assertEquals(Set.of("task2", "task3"), Set.copyOf(result.getNewUserTaskNodeIds()));
        assertTrue(result.getPendingJoinArrivals().isEmpty());
    }

    @Test
    @DisplayName("First branch to reach the join gateway should park there without completing")
    void firstBranchToReachJoinParksWithoutCompleting() {
        BpmnProcessDefinition parallelDef = BpmnGraphParser.parse(BpmnFixtures.PARALLEL_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(parallelDef, "task2", Map.of());

        assertTrue(result.getNewUserTaskNodeIds().isEmpty());
        assertFalse(result.isFullyResolved());
        assertEquals(Set.of("f5"), result.getPendingJoinArrivals());
    }

    @Test
    @DisplayName("Second branch to reach the join gateway should complete the process")
    void secondBranchToReachJoinCompletesProcess() {
        BpmnProcessDefinition parallelDef = BpmnGraphParser.parse(BpmnFixtures.PARALLEL_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(parallelDef, "task3", Map.of(), Set.of("f5"));

        assertTrue(result.isFullyResolved());
    }

    @Test
    @DisplayName("Inclusive gateway should only fork into the branches whose condition matched")
    void inclusiveGatewayForksOnlyMatchedBranches() {
        BpmnProcessDefinition inclusiveDef = BpmnGraphParser.parse(BpmnFixtures.INCLUSIVE_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(inclusiveDef, "task1", Map.of("highValue", true, "needsReview", false));

        assertEquals(List.of("task2"), result.getNewUserTaskNodeIds());
        assertTrue(result.getPendingJoinArrivals().isEmpty());
    }

    @Test
    @DisplayName("Inclusive gateway should fork into every matched branch when more than one condition matches")
    void inclusiveGatewayForksIntoAllMatchedBranches() {
        BpmnProcessDefinition inclusiveDef = BpmnGraphParser.parse(BpmnFixtures.INCLUSIVE_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(inclusiveDef, "task1", Map.of("highValue", true, "needsReview", true));

        assertEquals(Set.of("task2", "task3"), Set.copyOf(result.getNewUserTaskNodeIds()));
    }

    @Test
    @DisplayName("Inclusive join should fire immediately when the other branch was never activated")
    void inclusiveJoinFiresWhenOtherBranchWasNeverActivated() {
        BpmnProcessDefinition inclusiveDef = BpmnGraphParser.parse(BpmnFixtures.INCLUSIVE_PROCESS_XML);

        // Only task2 was activated by the fork (task3 never existed), so nothing else is
        // live in the instance - the join must not wait for f6.
        AdvanceResult result = ProcessEngine.advance(inclusiveDef, "task2", Map.of(), Set.of(), Set.of());

        assertTrue(result.isFullyResolved());
    }

    @Test
    @DisplayName("Inclusive join should wait when a sibling branch is still open elsewhere")
    void inclusiveJoinWaitsWhenSiblingBranchStillOpen() {
        BpmnProcessDefinition inclusiveDef = BpmnGraphParser.parse(BpmnFixtures.INCLUSIVE_PROCESS_XML);

        // Both branches were activated; task3 is still pending elsewhere, so the join must wait.
        AdvanceResult result = ProcessEngine.advance(inclusiveDef, "task2", Map.of(), Set.of(), Set.of("task3"));

        assertTrue(result.getNewUserTaskNodeIds().isEmpty());
        assertEquals(Set.of("f5"), result.getPendingJoinArrivals());
    }

    @Test
    @DisplayName("Inclusive join should complete once the last activated branch arrives")
    void inclusiveJoinCompletesOnceLastActivatedBranchArrives() {
        BpmnProcessDefinition inclusiveDef = BpmnGraphParser.parse(BpmnFixtures.INCLUSIVE_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(inclusiveDef, "task3", Map.of(), Set.of("f5"), Set.of());

        assertTrue(result.isFullyResolved());
    }

    @Test
    @DisplayName("Service tasks and business rule tasks should be walked through automatically, without creating a task")
    void serviceAndBusinessRuleTasksAreSkippedAutomatically() {
        BpmnProcessDefinition serviceDef = BpmnGraphParser.parse(BpmnFixtures.SERVICE_TASK_PROCESS_XML);

        AdvanceResult result = ProcessEngine.advance(serviceDef, serviceDef.getStartNodeId(), Map.of());

        assertEquals(List.of("task1"), result.getNewUserTaskNodeIds());
    }

    @Test
    @DisplayName("Business rule task should merge its DMN result into variables and a later gateway should see it in the same call")
    void businessRuleTaskDmnResultIsVisibleToLaterGateway() {
        BpmnProcessDefinition dmnDef = BpmnGraphParser.parse(BpmnFixtures.DMN_BUSINESS_RULE_PROCESS_XML);
        DmnDecisionEvaluator fakeEvaluator = (decisionRef, vars) -> {
            assertEquals("riskDecision", decisionRef);
            return Map.of("riskLevel", "HIGH");
        };

        AdvanceResult result = ProcessEngine.advance(dmnDef, dmnDef.getStartNodeId(), Map.of(), Set.of(), Set.of(), fakeEvaluator);

        assertEquals(List.of("task2"), result.getNewUserTaskNodeIds());
        assertEquals("HIGH", result.getUpdatedVariables().get("riskLevel"));
    }

    @Test
    @DisplayName("Business rule task should fall back to the gateway's default flow when the DMN result doesn't match")
    void businessRuleTaskDmnResultTakesDefaultGatewayBranch() {
        BpmnProcessDefinition dmnDef = BpmnGraphParser.parse(BpmnFixtures.DMN_BUSINESS_RULE_PROCESS_XML);
        DmnDecisionEvaluator fakeEvaluator = (decisionRef, vars) -> Map.of("riskLevel", "LOW");

        AdvanceResult result = ProcessEngine.advance(dmnDef, dmnDef.getStartNodeId(), Map.of(), Set.of(), Set.of(), fakeEvaluator);

        assertTrue(result.isFullyResolved());
        assertEquals("LOW", result.getUpdatedVariables().get("riskLevel"));
    }

    @Test
    @DisplayName("Should throw a clear error when a business rule task references a decision but no DMN evaluator was supplied")
    void businessRuleTaskWithoutEvaluatorThrows() {
        BpmnProcessDefinition dmnDef = BpmnGraphParser.parse(BpmnFixtures.DMN_BUSINESS_RULE_PROCESS_XML);

        AppException ex = assertThrows(AppException.class,
                () -> ProcessEngine.advance(dmnDef, dmnDef.getStartNodeId(), Map.of()));
        assertEquals(500, ex.getStatusCode());
    }
}
