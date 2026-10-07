package com.example.bpmn.engine;

/**
 * A single parsed BPMN flow-node (event/task/gateway). Not persisted - rebuilt from XML each time.
 *
 * <p>Most of the fields below only mean anything for one node type, so instances are built with
 * {@link #builder(String, BpmnNodeType)} and only the properties that apply are set; everything
 * else keeps its default. The javadoc on each getter says which type it belongs to.
 */
public class BpmnNode {
    private final String id;
    private final BpmnNodeType type;
    private final String name;
    private final String defaultFlowId;
    private final String decisionRef;
    private final String resultVariable;
    private final ConnectorBinding connectorBinding;
    private final String attachedToNodeId;
    private final String timerDuration;
    private final String timerDate;
    private final String timerCycle;
    private final boolean interrupting;

    private BpmnNode(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.name = builder.name;
        this.defaultFlowId = builder.defaultFlowId;
        this.decisionRef = builder.decisionRef;
        this.resultVariable = builder.resultVariable;
        this.connectorBinding = builder.connectorBinding;
        this.attachedToNodeId = builder.attachedToNodeId;
        this.timerDuration = builder.timerDuration;
        this.timerDate = builder.timerDate;
        this.timerCycle = builder.timerCycle;
        this.interrupting = builder.interrupting;
    }

    public static Builder builder(String id, BpmnNodeType type) {
        return new Builder(id, type);
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

    /** The connector this service task runs (from {@code <camunda:connector>}), or null - an unbound service task is walked straight through. */
    public ConnectorBinding getConnectorBinding() {
        return connectorBinding;
    }

    /** Node id this boundary event is attached to (from {@code attachedToRef}), or null unless this is a {@link BpmnNodeType#BOUNDARY_TIMER_EVENT}. */
    public String getAttachedToNodeId() {
        return attachedToNodeId;
    }

    /** ISO-8601 duration (e.g. {@code PT24H}), relative to task creation - null unless this timer uses {@code timeDuration}. */
    public String getTimerDuration() {
        return timerDuration;
    }

    /** ISO-8601 date-time - null unless this timer uses {@code timeDate}. */
    public String getTimerDate() {
        return timerDate;
    }

    /** ISO-8601 repeating interval (e.g. {@code R3/PT10M}, {@code R/PT10M}) - null unless this timer uses {@code timeCycle}. */
    public String getTimerCycle() {
        return timerCycle;
    }

    /** {@code cancelActivity} - true (default) unless this is a non-interrupting boundary event. Always true for non-boundary-event nodes. */
    public boolean isInterrupting() {
        return interrupting;
    }

    public static final class Builder {
        private final String id;
        private final BpmnNodeType type;
        private String name;
        private String defaultFlowId;
        private String decisionRef;
        private String resultVariable;
        private ConnectorBinding connectorBinding;
        private String attachedToNodeId;
        private String timerDuration;
        private String timerDate;
        private String timerCycle;
        private boolean interrupting = true;

        private Builder(String id, BpmnNodeType type) {
            this.id = id;
            this.type = type;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder defaultFlowId(String defaultFlowId) {
            this.defaultFlowId = defaultFlowId;
            return this;
        }

        public Builder decision(String decisionRef, String resultVariable) {
            this.decisionRef = decisionRef;
            this.resultVariable = resultVariable;
            return this;
        }

        public Builder connector(ConnectorBinding connectorBinding) {
            this.connectorBinding = connectorBinding;
            return this;
        }

        public Builder attachedToNodeId(String attachedToNodeId) {
            this.attachedToNodeId = attachedToNodeId;
            return this;
        }

        /** Exactly one of the three is expected to be non-null; the parser validates that before calling this. */
        public Builder timer(String timerDuration, String timerDate, String timerCycle) {
            this.timerDuration = timerDuration;
            this.timerDate = timerDate;
            this.timerCycle = timerCycle;
            return this;
        }

        public Builder interrupting(boolean interrupting) {
            this.interrupting = interrupting;
            return this;
        }

        public BpmnNode build() {
            return new BpmnNode(this);
        }
    }
}
