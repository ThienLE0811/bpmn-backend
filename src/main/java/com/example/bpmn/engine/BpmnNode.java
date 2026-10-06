package com.example.bpmn.engine;

/** A single parsed BPMN flow-node (event/task/gateway). Not persisted - rebuilt from XML each time. */
public class BpmnNode {
    private final String id;
    private final BpmnNodeType type;
    private final String name;
    private final String defaultFlowId;
    private final String decisionRef;
    private final String resultVariable;
    private final String attachedToNodeId;
    private final String timerDuration;
    private final String timerDate;
    private final String timerCycle;
    private final boolean interrupting;

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
        this.attachedToNodeId = null;
        this.timerDuration = null;
        this.timerDate = null;
        this.timerCycle = null;
        this.interrupting = true;
    }

    /** Only meaningful for {@link BpmnNodeType#BOUNDARY_TIMER_EVENT} - exactly one of {@code timerDuration}/{@code timerDate}/{@code timerCycle} is set. */
    public BpmnNode(String id, BpmnNodeType type, String name, String defaultFlowId,
                     String attachedToNodeId, String timerDuration, String timerDate, String timerCycle, boolean interrupting) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.defaultFlowId = defaultFlowId;
        this.decisionRef = null;
        this.resultVariable = null;
        this.attachedToNodeId = attachedToNodeId;
        this.timerDuration = timerDuration;
        this.timerDate = timerDate;
        this.timerCycle = timerCycle;
        this.interrupting = interrupting;
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

    /** Node id this boundary event is attached to (from {@code attachedToRef}), or null unless this is a {@link BpmnNodeType#BOUNDARY_TIMER_EVENT}. */
    public String getAttachedToNodeId() {
        return attachedToNodeId;
    }

    /** ISO-8601 duration (e.g. {@code PT24H}), relative to task creation - null unless this boundary timer uses {@code timeDuration}. */
    public String getTimerDuration() {
        return timerDuration;
    }

    /** ISO-8601 date-time - null unless this boundary timer uses {@code timeDate}. */
    public String getTimerDate() {
        return timerDate;
    }

    /** ISO-8601 repeating interval (e.g. {@code R3/PT10M}, {@code R/PT10M}) - null unless this boundary timer uses {@code timeCycle}. */
    public String getTimerCycle() {
        return timerCycle;
    }

    /** {@code cancelActivity} - true (default) unless this is a non-interrupting boundary event. Always true for non-boundary-event nodes. */
    public boolean isInterrupting() {
        return interrupting;
    }
}
