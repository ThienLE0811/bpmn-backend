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
