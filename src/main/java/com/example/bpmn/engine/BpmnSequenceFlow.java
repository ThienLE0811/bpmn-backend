package com.example.bpmn.engine;

/** A single parsed BPMN sequence flow (edge), with its optional condition expression. */
public class BpmnSequenceFlow {
    private final String id;
    private final String sourceRef;
    private final String targetRef;
    private final String conditionExpression;

    public BpmnSequenceFlow(String id, String sourceRef, String targetRef, String conditionExpression) {
        this.id = id;
        this.sourceRef = sourceRef;
        this.targetRef = targetRef;
        this.conditionExpression = conditionExpression;
    }

    public String getId() {
        return id;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }
}
