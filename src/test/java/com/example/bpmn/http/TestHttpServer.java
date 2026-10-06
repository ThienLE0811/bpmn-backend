package com.example.bpmn.http;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Runs a {@link BaseController} on a real loopback {@code HttpServer}, mirroring how
 * {@code RouteConfig}/{@code Main} mount controllers in production (one context per module, a
 * virtual-thread executor). The HTTP-layer tests go through it so they cover the whole request
 * path - CORS, route matching, authentication, handler dispatch, error-to-status mapping and
 * JSON encoding - rather than calling the handler classes directly.
 */
final class TestHttpServer implements AutoCloseable {

    /** Context prefix the test controllers are mounted under, e.g. production's "/api/users". */
    private static final String CONTEXT = "/api";

    private final HttpServer server;
    private final HttpClient client;
    private final String baseUrl;

    TestHttpServer(BaseController controller) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CONTEXT, controller);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * @param headerPairs alternating header name/value, e.g.
     *                    {@code send("GET", "/api/x", null, "Authorization", "Bearer ...")}
     */
    HttpResponse<String> send(String method, String path, String body, String... headerPairs) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        for (int i = 0; i < headerPairs.length; i += 2) {
            request.header(headerPairs[i], headerPairs[i + 1]);
        }

        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + method + " " + path, e);
        }
    }

    HttpResponse<String> get(String path) {
        return send("GET", path, null);
    }

    HttpResponse<String> get(String path, String bearerToken) {
        return send("GET", path, null, "Authorization", "Bearer " + bearerToken);
    }

    HttpResponse<String> post(String path, String body) {
        return send("POST", path, body, "Content-Type", "application/json");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
