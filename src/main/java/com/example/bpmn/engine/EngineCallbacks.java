package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;

/**
 * The callbacks {@link ProcessEngine#advance} needs to reach anything outside the pure graph walk.
 * Gathered into one object so adding a new kind of automatic step doesn't add another parameter to
 * {@code advance}, which already carries five.
 *
 * <p>Either callback may be passed as null, in which case a stub that fails loudly is substituted -
 * it is only ever invoked if the process actually contains the node type it serves, so a caller
 * that walks DMN-free, connector-free models never has to supply one.
 */
public record EngineCallbacks(DmnDecisionEvaluator dmnEvaluator, ConnectorInvoker connectorInvoker) {

    private static final DmnDecisionEvaluator NO_DMN_EVALUATOR = (decisionRef, vars) -> {
        throw new AppException("Business rule task references DMN decision \"" + decisionRef
                + "\" but no DMN evaluator was supplied to ProcessEngine.advance", 500);
    };

    private static final ConnectorInvoker NO_CONNECTOR_INVOKER = (connectorId, inputs) -> {
        throw new AppException("Service task references connector \"" + connectorId
                + "\" but no connector invoker was supplied to ProcessEngine.advance", 500);
    };

    public EngineCallbacks {
        dmnEvaluator = dmnEvaluator != null ? dmnEvaluator : NO_DMN_EVALUATOR;
        connectorInvoker = connectorInvoker != null ? connectorInvoker : NO_CONNECTOR_INVOKER;
    }

    /** For walks that are known to contain neither a bound business rule task nor a bound service task. */
    public static EngineCallbacks none() {
        return new EngineCallbacks(null, null);
    }

    public static EngineCallbacks of(DmnDecisionEvaluator dmnEvaluator) {
        return new EngineCallbacks(dmnEvaluator, null);
    }
}
