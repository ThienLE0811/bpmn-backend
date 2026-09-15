package com.example.bpmn.engine;

/** Outcome of walking a {@link BpmnProcessDefinition} forward from a given node. */
public class AdvanceResult {
    private final boolean completed;
    private final String nextNodeId;

    private AdvanceResult(boolean completed, String nextNodeId) {
        this.completed = completed;
        this.nextNodeId = nextNodeId;
    }

    public static AdvanceResult waitingAtUserTask(String nodeId) {
        return new AdvanceResult(false, nodeId);
    }

    public static AdvanceResult completed() {
        return new AdvanceResult(true, null);
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }
}
