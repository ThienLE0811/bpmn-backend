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
 * <p>A business rule task bound to a DMN decision (via {@code camunda:decisionRef}) is still
 * "pure" from the engine's perspective: evaluation itself is delegated to the caller-supplied
 * {@link DmnDecisionEvaluator}, and its result is merged into the variables carried through the
 * walk (and returned via {@link AdvanceResult#getUpdatedVariables()}), not persisted directly.
 */
public class ProcessEngine {
    private static final int MAX_HOPS = 1000;
    private static final JexlEngine JEXL = new JexlBuilder().create();

    /** Used when a caller doesn't supply a {@link DmnDecisionEvaluator} - only invoked (and only then fails) if a business rule task actually references a DMN decision. */
    private static final DmnDecisionEvaluator NO_DMN_EVALUATOR = (decisionRef, vars) -> {
        throw new AppException("Business rule task references DMN decision \"" + decisionRef
                + "\" but no DMN evaluator was supplied to ProcessEngine.advance", 500);
    };

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
        return advance(def, fromNodeId, variables, pendingJoinArrivals, otherActiveNodeIds, NO_DMN_EVALUATOR);
    }

    public static AdvanceResult advance(BpmnProcessDefinition def, String fromNodeId, Map<String, Object> variables,
                                         Set<String> pendingJoinArrivals, Set<String> otherActiveNodeIds,
                                         DmnDecisionEvaluator dmnEvaluator) {
        Map<String, Object> vars = new HashMap<>(variables != null ? variables : Map.of());
        Set<String> arrivals = new HashSet<>(pendingJoinArrivals != null ? pendingJoinArrivals : Set.of());
        Set<String> otherActive = otherActiveNodeIds != null ? otherActiveNodeIds : Set.of();
        List<String> waitingUserTasks = new ArrayList<>();

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
                applyDmnDecision(node, vars, dmnEvaluator);
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

        return new AdvanceResult(waitingUserTasks, arrivals, vars);
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
