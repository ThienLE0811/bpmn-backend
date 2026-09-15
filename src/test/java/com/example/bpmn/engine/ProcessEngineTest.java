package com.example.bpmn.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

        assertFalse(result.isCompleted());
        assertEquals("task1", result.getNextNodeId());
    }

    @Test
    @DisplayName("Should take the conditioned gateway branch when its expression is true")
    void gatewayTakesConditionedBranchWhenTrue() {
        AdvanceResult result = ProcessEngine.advance(definition, "task1", Map.of("amount", 5000));

        assertFalse(result.isCompleted());
        assertEquals("task2", result.getNextNodeId());
    }

    @Test
    @DisplayName("Should fall back to the default gateway branch when the condition is false")
    void gatewayFallsBackToDefaultBranchWhenConditionFalse() {
        AdvanceResult result = ProcessEngine.advance(definition, "task1", Map.of("amount", 100));

        assertTrue(result.isCompleted());
    }

    @Test
    @DisplayName("Should complete the instance after the second user task")
    void advanceFromSecondUserTaskReachesEnd() {
        AdvanceResult result = ProcessEngine.advance(definition, "task2", Map.of());

        assertTrue(result.isCompleted());
    }
}
