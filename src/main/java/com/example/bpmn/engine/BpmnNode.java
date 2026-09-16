package com.example.bpmn.engine;

/** A single parsed BPMN flow-node (event/task/gateway). Not persisted - rebuilt from XML each time. */
public class BpmnNode {
    private final String id;
    private final BpmnNodeType type;
    private final String name;
    private final String defaultFlowId;
    private final String decisionRef;
    private final String resultVariable;

    public BpmnNode(String id, BpmnNodeType type, String name, String defaultFlowId) {
        this(id, type, name, defaultFlowId, null, null);
    }

    /** Only meaningful for {@link BpmnNodeType#BUSINESS_RULE_TASK} - {@code decisionRef}/{@code resultVariable} are null otherwise. */
    public BpmnNode(String id, BpmnNodeType type, String name, String defaultFlowId, String decisionRef, String resultVariable) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.defaultFlowId = defaultFlowId;
        this.decisionRef = decisionRef;
        this.resultVariable = resultVariable;
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

    /** The DMN decision key (from {@code camunda:decisionRef}) this business rule task should evaluate, or null if unbound. */
    public String getDecisionRef() {
        return decisionRef;
    }

    /** Process variable name (from {@code camunda:resultVariable}) to store the decision result under, or null. */
    public String getResultVariable() {
        return resultVariable;
    }
}
