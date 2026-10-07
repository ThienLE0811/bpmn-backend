package com.example.bpmn.connector;

import com.example.bpmn.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorRegistryTest {

    /** Minimal connector that echoes back what it was given, so the registry is what's under test. */
    private static Connector echo(String id) {
        return new Connector() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> inputs) {
                return Map.of("calledId", id, "inputCount", inputs.size());
            }
        };
    }

    @Test
    @DisplayName("Should dispatch to the connector registered under the requested id")
    void dispatchesToRegisteredConnector() {
        ConnectorRegistry registry = new ConnectorRegistry(List.of(echo("http"), echo("mail")));

        Map<String, Object> result = registry.invoke("mail", Map.of("to", "a@b.c"));

        assertEquals("mail", result.get("calledId"));
        assertEquals(1, result.get("inputCount"));
        assertEquals(List.of("http", "mail"), List.copyOf(registry.getConnectorIds()));
    }

    @Test
    @DisplayName("Should report an unknown connector id with the ids that do exist")
    void rejectsUnknownConnectorId() {
        ConnectorRegistry registry = new ConnectorRegistry(List.of(echo("http")));

        AppException ex = assertThrows(AppException.class, () -> registry.invoke("smtp", Map.of()));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("smtp"));
        assertTrue(ex.getMessage().contains("http"));
    }

    @Test
    @DisplayName("Should refuse to build a registry where two connectors claim the same id")
    void rejectsDuplicateConnectorIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectorRegistry(List.of(echo("http"), echo("http"))));
    }

    @Test
    @DisplayName("Should turn a connector returning null into an empty result rather than a null map")
    void nullResultBecomesEmptyMap() {
        Connector nullReturning = new Connector() {
            @Override
            public String getId() {
                return "null";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> inputs) {
                return null;
            }
        };

        assertEquals(Map.of(), new ConnectorRegistry(List.of(nullReturning)).invoke("null", Map.of()));
    }
}
