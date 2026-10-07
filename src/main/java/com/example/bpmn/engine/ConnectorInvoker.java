package com.example.bpmn.engine;

import java.util.Map;

/**
 * Callback {@link ProcessEngine#advance} uses to run a service task's connector. Kept as an
 * injected interface (rather than calling an HTTP/mail client from inside the engine) for the
 * same reason as {@link DmnDecisionEvaluator}: {@code ProcessEngine} stays free of I/O and easy
 * to unit test, and the real implementation ({@code ConnectorRegistry}) is wired in by the caller.
 *
 * <p>The engine resolves the binding's input expressions before calling this and maps the
 * returned values back into process variables afterwards, so an implementation only ever sees a
 * flat map of already-evaluated values and never needs to know about BPMN, JEXL or variables.
 */
@FunctionalInterface
public interface ConnectorInvoker {
    /**
     * Runs the connector registered under {@code connectorId} with already-resolved {@code inputs}
     * and returns its result, whose entries the calling service task's output parameters read from.
     * May return an empty map but not null.
     */
    Map<String, Object> invoke(String connectorId, Map<String, Object> inputs);
}
