package com.example.bpmn.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A service task's binding to a connector, parsed from its {@code <camunda:connector>} extension
 * element. Immutable and never persisted - rebuilt from the BPMN XML on every parse, same as the
 * {@link BpmnNode} that owns it.
 *
 * <p>Input and output values are kept as the raw text written in the XML rather than being
 * evaluated here: a value wrapped in {@code ${...}} is a JEXL expression resolved at execution
 * time, anything else is a literal string. Inputs are resolved against the process variables and
 * handed to the connector; outputs are resolved against the map the connector returned, and each
 * resolved value is then stored into the process variable named by its key.
 *
 * <p>Both maps preserve the document order of the XML so a failure always reports the same
 * parameter first for the same model.
 */
public final class ConnectorBinding {
    private final String connectorId;
    private final Map<String, String> inputs;
    private final Map<String, String> outputs;

    public ConnectorBinding(String connectorId, Map<String, String> inputs, Map<String, String> outputs) {
        this.connectorId = connectorId;
        this.inputs = unmodifiableOrdered(inputs);
        this.outputs = unmodifiableOrdered(outputs);
    }

    private static Map<String, String> unmodifiableOrdered(Map<String, String> source) {
        return Collections.unmodifiableMap(source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>());
    }

    /** Id of the connector to run, from {@code <camunda:connectorId>} - looked up in the caller's connector registry. */
    public String getConnectorId() {
        return connectorId;
    }

    /** Parameter name to raw value, from {@code <camunda:inputParameter name="...">}. Resolved against the process variables. */
    public Map<String, String> getInputs() {
        return inputs;
    }

    /** Process variable name to raw value, from {@code <camunda:outputParameter name="...">}. Resolved against the connector's result map. */
    public Map<String, String> getOutputs() {
        return outputs;
    }
}
