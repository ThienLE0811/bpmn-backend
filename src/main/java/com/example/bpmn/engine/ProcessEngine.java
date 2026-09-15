package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure execution logic: given a parsed {@link BpmnProcessDefinition} and a node to start
 * from, walks the graph until it reaches a user task (waits there) or an end event
 * (completes). No persistence, no I/O - callers are responsible for saving the resulting
 * process instance/task state.
 */
public class ProcessEngine {
    private static final int MAX_HOPS = 1000;
    private static final JexlEngine JEXL = new JexlBuilder().create();

    private ProcessEngine() {
    }

    public static AdvanceResult advance(BpmnProcessDefinition def, String fromNodeId, Map<String, Object> variables) {
        Map<String, Object> vars = variables != null ? variables : Map.of();
        String currentNodeId = fromNodeId;

        for (int hops = 0; hops < MAX_HOPS; hops++) {
            BpmnNode currentNode = def.getNode(currentNodeId);
            if (currentNode == null) {
                throw new AppException("Unknown BPMN node: " + currentNodeId, 400);
            }

            String nextNodeId = currentNode.getType() == BpmnNodeType.EXCLUSIVE_GATEWAY
                    ? resolveGatewayOutcome(def, currentNode, vars)
                    : resolveSingleOutcome(def, currentNode);

            if (nextNodeId == null) {
                // Only reachable when currentNode is an end event with no outgoing flow.
                return AdvanceResult.completed();
            }

            BpmnNode nextNode = def.getNode(nextNodeId);
            if (nextNode == null) {
                throw new AppException("Unknown BPMN node: " + nextNodeId, 400);
            }
            if (nextNode.getType() == BpmnNodeType.USER_TASK) {
                return AdvanceResult.waitingAtUserTask(nextNodeId);
            }
            if (nextNode.getType() == BpmnNodeType.END_EVENT) {
                return AdvanceResult.completed();
            }
            currentNodeId = nextNodeId;
        }

        throw new AppException("Process definition appears to contain a cycle (exceeded " + MAX_HOPS + " hops)", 400);
    }

    private static String resolveSingleOutcome(BpmnProcessDefinition def, BpmnNode currentNode) {
        List<BpmnSequenceFlow> outgoing = def.getOutgoingFlows(currentNode.getId());
        if (outgoing.isEmpty()) {
            if (currentNode.getType() == BpmnNodeType.END_EVENT) {
                return null;
            }
            throw new AppException("Node " + currentNode.getId() + " has no outgoing sequence flow and is not an end event", 400);
        }
        if (outgoing.size() > 1) {
            throw new AppException("Node " + currentNode.getId() + " has multiple outgoing flows - parallel branching is not supported", 400);
        }
        return outgoing.get(0).getTargetRef();
    }

    private static String resolveGatewayOutcome(BpmnProcessDefinition def, BpmnNode gateway, Map<String, Object> variables) {
        List<BpmnSequenceFlow> outgoing = def.getOutgoingFlows(gateway.getId());
        if (outgoing.isEmpty()) {
            throw new AppException("Exclusive gateway " + gateway.getId() + " has no outgoing sequence flows", 400);
        }

        for (BpmnSequenceFlow flow : outgoing) {
            if (flow.getId().equals(gateway.getDefaultFlowId()) || flow.getConditionExpression() == null) {
                continue;
            }
            if (evaluateCondition(flow.getConditionExpression(), variables)) {
                return flow.getTargetRef();
            }
        }

        if (gateway.getDefaultFlowId() != null) {
            for (BpmnSequenceFlow flow : outgoing) {
                if (flow.getId().equals(gateway.getDefaultFlowId())) {
                    return flow.getTargetRef();
                }
            }
        }

        throw new AppException("No outgoing sequence flow condition matched at gateway " + gateway.getId()
                + " and no default flow is configured", 400);
    }

    private static boolean evaluateCondition(String expression, Map<String, Object> variables) {
        try {
            JexlContext context = new MapContext(new HashMap<>(variables));
            Object result = JEXL.createExpression(expression).evaluate(context);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            throw new AppException("Failed to evaluate gateway condition \"" + expression + "\": " + e.getMessage(), 400);
        }
    }
}
