package com.example.bpmn.connector;

import com.example.bpmn.exception.AppException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Drives {@link HttpConnector} against a real loopback server, so the actual request is exercised. */
class HttpConnectorTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private final HttpConnector connector = new HttpConnector(Duration.ofSeconds(2), Duration.ofSeconds(5), List.of("127.0.0.1"));

    private record RecordedRequest(String method, String body, String contentType, String authorization) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/ok", exchange -> {
            record(exchange);
            respond(exchange, 200, "{\"id\":42,\"status\":\"APPROVED\"}");
        });
        server.createContext("/text", exchange -> respond(exchange, 200, "plain text, not json"));
        server.createContext("/boom", exchange -> respond(exchange, 500, "{\"error\":\"nope\"}"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void record(HttpExchange exchange) throws IOException {
        lastRequest.set(new RecordedRequest(
                exchange.getRequestMethod(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                exchange.getRequestHeaders().getFirst("Authorization")));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    @DisplayName("Should GET by default and return the status, raw body and parsed json")
    void getReturnsStatusBodyAndJson() {
        Map<String, Object> result = connector.execute(Map.of("url", baseUrl + "/ok"));

        assertEquals("GET", lastRequest.get().method());
        assertEquals(200, result.get("statusCode"));
        assertEquals("{\"id\":42,\"status\":\"APPROVED\"}", result.get("body"));
        assertEquals(Map.of("id", 42, "status", "APPROVED"), result.get("json"));
    }

    @Test
    @DisplayName("Should send a map body as JSON and pass header.* inputs through as request headers")
    void postSendsJsonBodyAndHeaders() {
        connector.execute(Map.of(
                "url", baseUrl + "/ok",
                "method", "post",
                "body", Map.of("amount", 10),
                "header.Authorization", "Bearer t0ken"));

        RecordedRequest request = lastRequest.get();
        assertEquals("POST", request.method());
        assertEquals("{\"amount\":10}", request.body());
        assertEquals("application/json", request.contentType());
        assertEquals("Bearer t0ken", request.authorization());
    }

    @Test
    @DisplayName("A non-JSON response should still return its body, with json left null")
    void nonJsonResponseLeavesJsonNull() {
        Map<String, Object> result = connector.execute(Map.of("url", baseUrl + "/text"));

        assertEquals("plain text, not json", result.get("body"));
        assertNull(result.get("json"));
    }

    @Test
    @DisplayName("Should fail on a non-2xx response by default, so the service task raises an incident")
    void nonSuccessStatusFailsByDefault() {
        Map<String, Object> inputs = Map.of("url", baseUrl + "/boom");

        AppException ex = assertThrows(AppException.class, () -> connector.execute(inputs));

        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

    @Test
    @DisplayName("failOnError=false should hand the failing status back to the model instead of throwing")
    void nonSuccessStatusCanBeHandledByTheModel() {
        Map<String, Object> result = connector.execute(Map.of(
                "url", baseUrl + "/boom",
                "failOnError", false));

        assertEquals(500, result.get("statusCode"));
    }

    @Test
    @DisplayName("Should reject a call with no url rather than failing deep inside the HTTP client")
    void missingUrlIsRejected() {
        Map<String, Object> inputs = Map.of("method", "GET");

        AppException ex = assertThrows(AppException.class, () -> connector.execute(inputs));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("url"));
    }

    @Test
    @DisplayName("Should report an unreachable host as a connector failure")
    void unreachableHostFails() {
        // Port 1 on loopback: nothing listens there, so the connection is refused immediately.
        Map<String, Object> inputs = Map.of("url", "http://127.0.0.1:1/nothing");

        assertThrows(AppException.class, () -> connector.execute(inputs));
    }

    @Test
    @DisplayName("Should refuse a host that is not in the allow-list, before any request leaves the process")
    void hostOutsideAllowListIsRefused() {
        HttpConnector restricted = new HttpConnector(Duration.ofSeconds(2), Duration.ofSeconds(5), List.of("api.example.com"));
        Map<String, Object> inputs = Map.of("url", baseUrl + "/ok");

        AppException ex = assertThrows(AppException.class, () -> restricted.execute(inputs));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("127.0.0.1"));
        assertNull(lastRequest.get(), "the request must not have been sent");
    }

    @Test
    @DisplayName("An empty allow-list should refuse everything, so an unconfigured deployment cannot be used to probe the network")
    void emptyAllowListRefusesEverything() {
        HttpConnector denyAll = new HttpConnector(Duration.ofSeconds(2), Duration.ofSeconds(5), List.of());
        Map<String, Object> inputs = Map.of("url", baseUrl + "/ok");

        assertEquals(403, assertThrows(AppException.class, () -> denyAll.execute(inputs)).getStatusCode());
    }

    @Test
    @DisplayName("A *.suffix entry should cover sub-domains but not the bare domain")
    void subdomainWildcardCoversSubdomainsOnly() {
        HttpConnector restricted = new HttpConnector(Duration.ofSeconds(2), Duration.ofSeconds(5), List.of("*.example.com"));

        // Asserted on the policy itself rather than by calling the hosts: whether a name is
        // allowed must not depend on whether it happens to resolve from the machine running this.
        assertTrue(restricted.isHostAllowed("api.example.com"));
        assertTrue(restricted.isHostAllowed("API.Example.COM"));
        assertFalse(restricted.isHostAllowed("example.com"));
        assertFalse(restricted.isHostAllowed("notexample.com"));
        assertFalse(restricted.isHostAllowed("example.com.evil.net"));
    }

    @Test
    @DisplayName("An exact entry should match that host only, case-insensitively")
    void exactEntryMatchesThatHostOnly() {
        HttpConnector restricted = new HttpConnector(Duration.ofSeconds(2), Duration.ofSeconds(5), List.of("api.example.com"));

        assertTrue(restricted.isHostAllowed("api.example.com"));
        assertTrue(restricted.isHostAllowed("Api.Example.com"));
        assertFalse(restricted.isHostAllowed("other.example.com"));
        assertFalse(restricted.isHostAllowed("evil.com"));
    }

    @Test
    @DisplayName("A single \"*\" should switch the host check off for development")
    void wildcardAllowsAnyHost() {
        HttpConnector unrestricted = new HttpConnector(Duration.ofSeconds(2), Duration.ofSeconds(5), List.of("*"));

        assertEquals(200, unrestricted.execute(Map.of("url", baseUrl + "/ok")).get("statusCode"));
    }

    @Test
    @DisplayName("Should refuse a non-http scheme rather than handing it to the HTTP client")
    void nonHttpSchemeIsRefused() {
        Map<String, Object> inputs = Map.of("url", "file:///etc/passwd");

        AppException ex = assertThrows(AppException.class, () -> connector.execute(inputs));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("http or https"));
    }
}
