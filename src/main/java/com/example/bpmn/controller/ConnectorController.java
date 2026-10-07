package com.example.bpmn.controller;

import com.example.bpmn.connector.ConnectorRegistry;
import com.example.bpmn.http.BaseController;

import java.util.List;

/**
 * Lists the connector ids a service task may bind to, so the BPMN designer can offer them
 * instead of asking the modeller to remember the spelling.
 *
 * <p>Read-only by design: connectors are registered in code, not stored, so there is nothing
 * to create or delete here - see {@link ConnectorRegistry}.
 */
public class ConnectorController extends BaseController {

    public ConnectorController(ConnectorRegistry connectorRegistry) {
        get("/api/connectors", ctx -> List.copyOf(connectorRegistry.getConnectorIds()));
    }
}
