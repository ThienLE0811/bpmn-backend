package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BpmnGraphParserTest {

    @Test
    @DisplayName("Should parse start/user task/gateway/end nodes")
    void parsesStartUserTaskGatewayAndEndNodes() {
        BpmnProcessDefinition def = BpmnGraphParser.parse(BpmnFixtures.APPROVAL_PROCESS_XML);

        assertEquals("approval_process", def.getProcessId());
        assertEquals("start1", def.getStartNodeId());

        assertEquals(BpmnNodeType.START_EVENT, def.getNode("start1").getType());
        assertEquals(BpmnNodeType.USER_TASK, def.getNode("task1").getType());
        assertEquals("Review Request", def.getNode("task1").getName());
        assertEquals(BpmnNodeType.EXCLUSIVE_GATEWAY, def.getNode("gw1").getType());
        assertEquals("flow4", def.getNode("gw1").getDefaultFlowId());
        assertEquals(BpmnNodeType.END_EVENT, def.getNode("end1").getType());
        assertEquals(BpmnNodeType.END_EVENT, def.getNode("end2").getType());
    }

    @Test
    @DisplayName("Should parse sequence flow conditions and strip the ${...} wrapper")
    void parsesSequenceFlowConditionsAndStripsExpressionWrapper() {
        BpmnProcessDefinition def = BpmnGraphParser.parse(BpmnFixtures.APPROVAL_PROCESS_XML);

        List<BpmnSequenceFlow> gatewayFlows = def.getOutgoingFlows("gw1");
        assertEquals(2, gatewayFlows.size());

        BpmnSequenceFlow conditioned = gatewayFlows.stream()
                .filter(f -> f.getId().equals("flow3"))
                .findFirst().orElseThrow();
        assertEquals("amount > 1000", conditioned.getConditionExpression());

        BpmnSequenceFlow defaultFlow = gatewayFlows.stream()
                .filter(f -> f.getId().equals("flow4"))
                .findFirst().orElseThrow();
        assertNull(defaultFlow.getConditionExpression());
    }

    @Test
    @DisplayName("Should parse parallel gateways and index incoming/outgoing flows for join detection")
    void parsesParallelGatewaysAndIndexesIncomingFlows() {
        BpmnProcessDefinition def = BpmnGraphParser.parse(BpmnFixtures.PARALLEL_PROCESS_XML);

        assertEquals(BpmnNodeType.PARALLEL_GATEWAY, def.getNode("pgFork").getType());
        assertEquals(BpmnNodeType.PARALLEL_GATEWAY, def.getNode("pgJoin").getType());

        assertEquals(2, def.getOutgoingFlows("pgFork").size());
        assertEquals(2, def.getIncomingFlows("pgJoin").size());
        assertTrue(def.getIncomingFlows("pgFork").isEmpty() || def.getIncomingFlows("pgFork").size() == 1);
        assertEquals(1, def.getOutgoingFlows("pgJoin").size());
    }

    @Test
    @DisplayName("Should parse inclusive gateways")
    void parsesInclusiveGateways() {
        BpmnProcessDefinition def = BpmnGraphParser.parse(BpmnFixtures.INCLUSIVE_PROCESS_XML);

        assertEquals(BpmnNodeType.INCLUSIVE_GATEWAY, def.getNode("igFork").getType());
        assertEquals(BpmnNodeType.INCLUSIVE_GATEWAY, def.getNode("igJoin").getType());
        assertEquals(2, def.getOutgoingFlows("igFork").size());
        assertEquals(2, def.getIncomingFlows("igJoin").size());
    }

    @Test
    @DisplayName("Should parse service tasks and business rule tasks")
    void parsesServiceAndBusinessRuleTasks() {
        BpmnProcessDefinition def = BpmnGraphParser.parse(BpmnFixtures.SERVICE_TASK_PROCESS_XML);

        assertEquals(BpmnNodeType.SERVICE_TASK, def.getNode("svc1").getType());
        assertEquals(BpmnNodeType.BUSINESS_RULE_TASK, def.getNode("brt1").getType());
        assertNull(def.getNode("brt1").getDecisionRef());
        assertNull(def.getNode("brt1").getResultVariable());
    }

    @Test
    @DisplayName("Should parse camunda:decisionRef/resultVariable on a business rule task")
    void parsesBusinessRuleTaskDmnBinding() {
        BpmnProcessDefinition def = BpmnGraphParser.parse(BpmnFixtures.DMN_BUSINESS_RULE_PROCESS_XML);

        BpmnNode brt = def.getNode("brt1");
        assertEquals(BpmnNodeType.BUSINESS_RULE_TASK, brt.getType());
        assertEquals("riskDecision", brt.getDecisionRef());
        assertEquals("riskLevel", brt.getResultVariable());
    }

    @Test
    @DisplayName("Should reject XML without exactly one start event")
    void rejectsXmlWithoutExactlyOneStartEvent() {
        String xmlNoStart = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs">
                  <process id="p1">
                    <endEvent id="end1" />
                  </process>
                </definitions>
                """;

        AppException ex = assertThrows(AppException.class, () -> BpmnGraphParser.parse(xmlNoStart));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject a sequence flow referencing an unknown node")
    void rejectsSequenceFlowReferencingUnknownNode() {
        String malformed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs">
                  <process id="p1">
                    <startEvent id="start1" />
                    <endEvent id="end1" />
                    <sequenceFlow id="flow1" sourceRef="start1" targetRef="doesNotExist" />
                  </process>
                </definitions>
                """;

        AppException ex = assertThrows(AppException.class, () -> BpmnGraphParser.parse(malformed));
        assertEquals(400, ex.getStatusCode());
    }
}
