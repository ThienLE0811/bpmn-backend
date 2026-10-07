package com.example.bpmn.connector;

import com.example.bpmn.engine.ConnectorInvoker;
import com.example.bpmn.exception.AppException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The set of connectors this deployment knows how to run, keyed by the id a service task
 * references. Implements {@link ConnectorInvoker} so it can be handed straight to the engine.
 *
 * <p>Connectors are registered in code rather than loaded from the database: the point of storing
 * connector definitions would be to let users add new kinds of integration at runtime, which this
 * single-tenant deployment has no need for yet. What the BPMN model configures is the parameters
 * of a call, not the code that makes it.
 */
public class ConnectorRegistry implements ConnectorInvoker {
    private final Map<String, Connector> connectorsById;

    public ConnectorRegistry(List<Connector> connectors) {
        Map<String, Connector> byId = new LinkedHashMap<>();
        for (Connector connector : connectors) {
            Connector previous = byId.put(connector.getId(), connector);
            if (previous != null) {
                throw new IllegalArgumentException("Two connectors are registered under the id \"" + connector.getId() + "\"");
            }
        }
        this.connectorsById = Collections.unmodifiableMap(byId);
    }

    /** Ids a service task may reference, in registration order. */
    public Set<String> getConnectorIds() {
        return connectorsById.keySet();
    }

    @Override
    public Map<String, Object> invoke(String connectorId, Map<String, Object> inputs) {
        Connector connector = connectorsById.get(connectorId);
        if (connector == null) {
            throw new AppException("No connector is registered under the id \"" + connectorId
                    + "\" - known ids are " + connectorsById.keySet(), 400);
        }
        Map<String, Object> result = connector.execute(inputs);
        return result != null ? result : Map.of();
    }
}
