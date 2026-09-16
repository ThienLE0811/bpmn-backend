package com.example.bpmn.engine;

import java.util.List;
import java.util.Map;

/** In-memory graph parsed from a single BPMN process's XML. Never persisted - rebuilt on demand. */
public class BpmnProcessDefinition {
    private final String processId;
    private final Map<String, BpmnNode> nodesById;
    private final Map<String, List<BpmnSequenceFlow>> outgoingFlowsByNodeId;
    private final Map<String, List<BpmnSequenceFlow>> incomingFlowsByNodeId;
    private final String startNodeId;

    public BpmnProcessDefinition(String processId, Map<String, BpmnNode> nodesById,
                                  Map<String, List<BpmnSequenceFlow>> outgoingFlowsByNodeId,
                                  Map<String, List<BpmnSequenceFlow>> incomingFlowsByNodeId, String startNodeId) {
        this.processId = processId;
        this.nodesById = nodesById;
        this.outgoingFlowsByNodeId = outgoingFlowsByNodeId;
        this.incomingFlowsByNodeId = incomingFlowsByNodeId;
        this.startNodeId = startNodeId;
    }

    public String getProcessId() {
        return processId;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public BpmnNode getNode(String nodeId) {
        return nodesById.get(nodeId);
    }

    public List<BpmnSequenceFlow> getOutgoingFlows(String nodeId) {
        return outgoingFlowsByNodeId.getOrDefault(nodeId, List.of());
    }

    /** Flows arriving at a parallel gateway - used by {@link ProcessEngine} to detect join synchronization. */
    public List<BpmnSequenceFlow> getIncomingFlows(String nodeId) {
        return incomingFlowsByNodeId.getOrDefault(nodeId, List.of());
    }
}
