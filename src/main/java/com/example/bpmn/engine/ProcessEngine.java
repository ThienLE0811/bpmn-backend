package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure execution logic: given a parsed {@link BpmnProcessDefinition} and a node to resume
 * from, walks the graph until every live branch reaches a user task (waits there) or an end
 * event (that branch finishes). No persistence, no I/O - callers own saving the resulting
 * process instance/task state.
 *
 * <p>A parallel gateway forks into every outgoing flow unconditionally, and its join waits
 * for literally all incoming flows to arrive. An inclusive gateway forks into every outgoing
 * flow whose condition matches (falling back to its default flow if none match, same rule as
 * exclusive gateways) - so anywhere from one to all branches may activate - and its join only
 * waits for the incoming flows that some other still-open token could still reach; a branch
 * that was never activated at the matching split is never waited on. "Still-open" is
 * determined by structural reachability from {@code otherActiveNodeIds} (the node ids of every
 * other task currently pending/claimed elsewhere in the same process instance), since a live
 * token sitting at an upstream task can still travel forward and eventually arrive.
 *
 * <p>Because sibling branches usually finish in separate {@code advance} calls (e.g. two
 * different user tasks completed at different times), callers must persist
 * {@code pendingJoinArrivals} on the process instance and pass it back in on every subsequent
 * call for that instance, and must also pass the up-to-date {@code otherActiveNodeIds} so
 * inclusive joins can tell which branches are still expected.
 *
 * <p>A business rule task bound to a DMN decision (via {@code camunda:decisionRef}) and a service
 * task bound to a connector (via {@code <camunda:connector>}) are both still "pure" from the
 * engine's perspective: the evaluation or call itself is delegated to the caller-supplied
 * {@link EngineCallbacks}, and the result is merged into the variables carried through the walk
 * (and returned via {@link AdvanceResult#getUpdatedVariables()}), not persisted directly.
 */
public class ProcessEngine {
    private static final int MAX_HOPS = 1000;
    private static final JexlEngine JEXL = new JexlBuilder().create();

    private ProcessEngine() {
    }

    private record Token(String nodeId, String viaFlowId) {
    }

    public static AdvanceResult advance(BpmnProcessDefinition def, String fromNodeId, Map<String, Object> variables) {
        return advance(def, fromNodeId, variables, Set.of(), Set.of());
    }

    public static AdvanceResult advance(BpmnProcessDefinition def, String fromNodeId, Map<String, Object> variables,
                                         Set<String> pendingJoinArrivals) {
        return advance(def, fromNodeId, variables, pendingJoinArrivals, Set.of());
    }

    public static AdvanceResult advance(BpmnProcessDefinition def, String fromNodeId, Map<String, Object> variables,
                                         Set<String> pendingJoinArrivals, Set<String> otherActiveNodeIds) {
        return advance(def, fromNodeId, variables, pendingJoinArrivals, otherActiveNodeIds, EngineCallbacks.none());
    }

    public static AdvanceResult advance(BpmnProcessDefinition def, String fromNodeId, Map<String, Object> variables,
                                         Set<String> pendingJoinArrivals, Set<String> otherActiveNodeIds,
                                         EngineCallbacks callbacks) {
        EngineCallbacks effectiveCallbacks = callbacks != null ? callbacks : EngineCallbacks.none();
        Map<String, Object> vars = new HashMap<>(variables != null ? variables : Map.of());
        Set<String> arrivals = new HashSet<>(pendingJoinArrivals != null ? pendingJoinArrivals : Set.of());
        Set<String> otherActive = otherActiveNodeIds != null ? otherActiveNodeIds : Set.of();
        List<String> waitingUserTasks = new ArrayList<>();
        List<String> waitingTimerNodes = new ArrayList<>();

        Deque<Token> workList = new ArrayDeque<>();
        workList.push(new Token(fromNodeId, null));

        int hops = 0;
        while (!workList.isEmpty()) {
            if (++hops > MAX_HOPS) {
                throw new AppException("Process definition appears to contain a cycle (exceeded " + MAX_HOPS + " hops)", 400);
            }
            Token token = workList.pop();
            BpmnNode node = def.getNode(token.nodeId());
            if (node == null) {
                throw new AppException("Unknown BPMN node: " + token.nodeId(), 400);
            }

            if (node.getType() == BpmnNodeType.USER_TASK && !token.nodeId().equals(fromNodeId)) {
                // Newly reached (not the task the caller just completed) - stop and wait here.
                waitingUserTasks.add(token.nodeId());
                continue;
            }
            if (node.getType() == BpmnNodeType.END_EVENT) {
                // This branch has finished; nothing more to push for it.
                continue;
            }
            if (node.getType() == BpmnNodeType.INTERMEDIATE_CATCH_TIMER_EVENT && !token.nodeId().equals(fromNodeId)) {
                // Newly reached (not the timer that just fired) - stop and wait here.
                waitingTimerNodes.add(token.nodeId());
                continue;
            }
            if (node.getType() == BpmnNodeType.PARALLEL_GATEWAY || node.getType() == BpmnNodeType.INCLUSIVE_GATEWAY) {
                List<BpmnSequenceFlow> incoming = def.getIncomingFlows(node.getId());
                if (incoming.size() > 1) {
                    if (token.viaFlowId() == null) {
                        throw new AppException("Join gateway " + node.getId() + " was reached without a sequence flow", 400);
                    }
                    arrivals.add(token.viaFlowId());
                    boolean satisfied = node.getType() == BpmnNodeType.PARALLEL_GATEWAY
                            ? incoming.stream().allMatch(flow -> arrivals.contains(flow.getId()))
                            : isInclusiveJoinSatisfied(def, incoming, arrivals, otherActive);
                    if (!satisfied) {
                        // Waiting on sibling branch(es); this token parks here.
                        continue;
                    }
                    incoming.forEach(flow -> arrivals.remove(flow.getId()));
                }

                List<BpmnSequenceFlow> outgoing = node.getType() == BpmnNodeType.PARALLEL_GATEWAY
                        ? def.getOutgoingFlows(node.getId())
                        : resolveInclusiveGatewayOutcomes(def, node, vars);
                for (BpmnSequenceFlow flow : outgoing) {
                    workList.push(new Token(flow.getTargetRef(), flow.getId()));
                }
                continue;
            }

            if (node.getType() == BpmnNodeType.BUSINESS_RULE_TASK && node.getDecisionRef() != null) {
                applyDmnDecision(node, vars, effectiveCallbacks.dmnEvaluator());
            }
            if (node.getType() == BpmnNodeType.SERVICE_TASK && node.getConnectorBinding() != null) {
                applyConnector(node, vars, effectiveCallbacks.connectorInvoker());
            }

            String nextFlowId;
            String nextNodeId;
            if (node.getType() == BpmnNodeType.EXCLUSIVE_GATEWAY) {
                BpmnSequenceFlow flow = resolveExclusiveGatewayOutcome(def, node, vars);
                nextFlowId = flow.getId();
                nextNodeId = flow.getTargetRef();
            } else {
                BpmnSequenceFlow flow = resolveSingleOutgoing(def, node);
                nextFlowId = flow.getId();
                nextNodeId = flow.getTargetRef();
            }
            workList.push(new Token(nextNodeId, nextFlowId));
        }

        return new AdvanceResult(waitingUserTasks, waitingTimerNodes, arrivals, vars);
    }

    /** Evaluates the business rule task's DMN decision and merges the result into {@code vars} (in place) so a gateway reached later in the same walk can branch on it. */
    private static void applyDmnDecision(BpmnNode node, Map<String, Object> vars, DmnDecisionEvaluator dmnEvaluator) {
        Map<String, Object> decisionResult = dmnEvaluator.evaluate(node.getDecisionRef(), vars);
        vars.putAll(decisionResult);
        if (node.getResultVariable() != null) {
            Object value;
            if (decisionResult.size() == 1) {
                value = decisionResult.values().iterator().next();
            } else if (decisionResult.isEmpty()) {
                value = null;
            } else {
                value = decisionResult;
            }
            vars.put(node.getResultVariable(), value);
        }
    }

    /**
     * Resolves the service task's connector inputs against {@code vars}, hands them to the
     * invoker, then writes each output back into {@code vars} (in place) so a gateway reached
     * later in the same walk can branch on what the connector returned.
     *
     * <p>Every failure here - a bad expression, an unknown connector, an error raised by the
     * connector itself - is reported as a {@link ConnectorException} naming this node, so the
     * caller can record an incident against it rather than losing the walk's instance state.
     */
    private static void applyConnector(BpmnNode node, Map<String, Object> vars, ConnectorInvoker invoker) {
        ConnectorBinding binding = node.getConnectorBinding();
        try {
            Map<String, Object> inputs = new LinkedHashMap<>();
            binding.getInputs().forEach((name, rawValue) -> inputs.put(name, resolveValue(rawValue, vars)));

            Map<String, Object> result = invoker.invoke(binding.getConnectorId(), inputs);
            // Outputs read from what the connector returned, not from the process variables.
            Map<String, Object> resultScope = result != null ? result : Map.of();
            binding.getOutputs().forEach((variableName, rawValue) -> vars.put(variableName, resolveValue(rawValue, resultScope)));
        } catch (Exception e) {
            throw new ConnectorException(node.getId(), binding.getConnectorId(), e.getMessage(), e);
        }
    }

    /**
     * A connector parameter value: {@code ${...}} is a JEXL expression evaluated against
     * {@code scope}, anything else is the literal text as written in the BPMN XML.
     */
    private static Object resolveValue(String rawValue, Map<String, Object> scope) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("${") || !trimmed.endsWith("}")) {
            return rawValue;
        }
        String expression = trimmed.substring(2, trimmed.length() - 1).trim();
        if (expression.contains("${")) {
            throw new AppException("Value \"" + rawValue
                    + "\" mixes text and expressions - a parameter must be either one whole ${...} expression or plain text", 400);
        }
        try {
            JexlContext context = new MapContext(new HashMap<>(scope));
            return JEXL.createExpression(expression).evaluate(context);
        } catch (Exception e) {
            throw new AppException("Failed to evaluate expression \"" + expression + "\": " + e.getMessage(), 400);
        }
    }

    /** Only called for node types that are never an end event (that case is handled before this is reached). */
    private static BpmnSequenceFlow resolveSingleOutgoing(BpmnProcessDefinition def, BpmnNode currentNode) {
        List<BpmnSequenceFlow> outgoing = def.getOutgoingFlows(currentNode.getId());
        if (outgoing.isEmpty()) {
            throw new AppException("Node " + currentNode.getId() + " has no outgoing sequence flow and is not an end event", 400);
        }
        if (outgoing.size() > 1) {
            throw new AppException("Node " + currentNode.getId()
                    + " has multiple outgoing flows but is not a parallel/inclusive gateway - branching is not supported here", 400);
        }
        return outgoing.get(0);
    }

    private static BpmnSequenceFlow resolveExclusiveGatewayOutcome(BpmnProcessDefinition def, BpmnNode gateway, Map<String, Object> variables) {
        List<BpmnSequenceFlow> outgoing = def.getOutgoingFlows(gateway.getId());
        if (outgoing.isEmpty()) {
            throw new AppException("Exclusive gateway " + gateway.getId() + " has no outgoing sequence flows", 400);
        }

        for (BpmnSequenceFlow flow : outgoing) {
            if (flow.getId().equals(gateway.getDefaultFlowId()) || flow.getConditionExpression() == null) {
                continue;
            }
            if (evaluateCondition(flow.getConditionExpression(), variables)) {
                return flow;
            }
        }

        if (gateway.getDefaultFlowId() != null) {
            for (BpmnSequenceFlow flow : outgoing) {
                if (flow.getId().equals(gateway.getDefaultFlowId())) {
                    return flow;
                }
            }
        }

        throw new AppException("No outgoing sequence flow condition matched at gateway " + gateway.getId()
                + " and no default flow is configured", 400);
    }

    /** Unlike an exclusive gateway, every matching flow is taken (not just the first one). */
    private static List<BpmnSequenceFlow> resolveInclusiveGatewayOutcomes(BpmnProcessDefinition def, BpmnNode gateway, Map<String, Object> variables) {
        List<BpmnSequenceFlow> outgoing = def.getOutgoingFlows(gateway.getId());
        if (outgoing.isEmpty()) {
            throw new AppException("Inclusive gateway " + gateway.getId() + " has no outgoing sequence flows", 400);
        }

        List<BpmnSequenceFlow> matched = new ArrayList<>();
        for (BpmnSequenceFlow flow : outgoing) {
            if (flow.getId().equals(gateway.getDefaultFlowId())) {
                continue;
            }
            if (flow.getConditionExpression() == null || evaluateCondition(flow.getConditionExpression(), variables)) {
                matched.add(flow);
            }
        }

        if (matched.isEmpty() && gateway.getDefaultFlowId() != null) {
            outgoing.stream()
                    .filter(flow -> flow.getId().equals(gateway.getDefaultFlowId()))
                    .findFirst()
                    .ifPresent(matched::add);
        }

        if (matched.isEmpty()) {
            throw new AppException("No outgoing sequence flow condition matched at inclusive gateway " + gateway.getId()
                    + " and no default flow is configured", 400);
        }
        return matched;
    }

    /** An inclusive join fires once every incoming flow has either arrived or can no longer be reached by any live token. */
    private static boolean isInclusiveJoinSatisfied(BpmnProcessDefinition def, List<BpmnSequenceFlow> incoming,
                                                      Set<String> arrivals, Set<String> otherActiveNodeIds) {
        for (BpmnSequenceFlow flow : incoming) {
            if (arrivals.contains(flow.getId())) {
                continue;
            }
            boolean stillExpected = otherActiveNodeIds.stream().anyMatch(nodeId -> canReach(def, nodeId, flow.getSourceRef()));
            if (stillExpected) {
                return false;
            }
        }
        return true;
    }

    /** Structural forward reachability (ignores runtime conditions - an edge is traversable if it could ever be taken). */
    private static boolean canReach(BpmnProcessDefinition def, String fromNodeId, String toNodeId) {
        if (fromNodeId.equals(toNodeId)) {
            return true;
        }
        Set<String> visited = new HashSet<>();
        visited.add(fromNodeId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (BpmnSequenceFlow flow : def.getOutgoingFlows(current)) {
                String next = flow.getTargetRef();
                if (next.equals(toNodeId)) {
                    return true;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
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
