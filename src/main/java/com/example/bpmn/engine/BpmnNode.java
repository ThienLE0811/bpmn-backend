package com.example.bpmn.engine;

/** A single parsed BPMN flow-node (event/task/gateway). Not persisted - rebuilt from XML each time. */
public class BpmnNode {
    private final String id;
    private final BpmnNodeType type;
    private final String name;
    private final String defaultFlowId;

    public BpmnNode(String id, BpmnNodeType type, String name, String defaultFlowId) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.defaultFlowId = defaultFlowId;
    }

    public String getId() {
        return id;
    }

    public BpmnNodeType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDefaultFlowId() {
        return defaultFlowId;
    }
}
