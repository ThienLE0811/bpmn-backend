package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;

/**
 * Thrown when a service task's connector cannot be run to completion - an unresolvable input
 * expression, an unknown connector id, or a failure raised by the connector itself.
 *
 * <p>Distinct from a plain {@link AppException} because callers treat it differently: every other
 * engine failure is a modelling error that aborts the request, whereas this one happens partway
 * through a walk of an instance that already exists, so services catch it and record an incident
 * on that instance instead of letting the exception discard its state. {@link #getNodeId()} is the
 * service task to report the incident against, and to resume from once the cause is fixed.
 */
public class ConnectorException extends AppException {
    private final String nodeId;
    private final String connectorId;

    public ConnectorException(String nodeId, String connectorId, String message, Throwable cause) {
        super("Connector \"" + connectorId + "\" on service task " + nodeId + " failed: " + message, cause, 500);
        this.nodeId = nodeId;
        this.connectorId = connectorId;
    }

    /** Id of the service task whose connector failed. */
    public String getNodeId() {
        return nodeId;
    }

    public String getConnectorId() {
        return connectorId;
    }
}
