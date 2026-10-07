package com.example.bpmn.connector;

import java.util.Map;

/**
 * One kind of outside call a service task can make - an HTTP request, an email, and so on.
 * This is the layer that is allowed to do I/O; the engine itself only ever sees the
 * {@link com.example.bpmn.engine.ConnectorInvoker} interface.
 *
 * <p>Implementations receive input values that the engine has already resolved from the BPMN
 * model (so no expressions, no process variables) and return a flat map that the service task's
 * output parameters read from. Failures are reported by throwing - the engine turns that into a
 * {@link com.example.bpmn.engine.ConnectorException} naming the service task, which the calling
 * service records as an incident on the process instance.
 */
public interface Connector {

    /** Id this connector is registered under, matching {@code <camunda:connectorId>} in the BPMN XML. */
    String getId();

    /** Runs the call. Must return a map (possibly empty), never null. */
    Map<String, Object> execute(Map<String, Object> inputs);
}
