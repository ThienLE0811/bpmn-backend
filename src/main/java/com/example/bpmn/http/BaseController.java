package com.example.bpmn.http;

import com.example.bpmn.dto.ApiResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.util.JsonUtil;
import com.example.bpmn.util.JwtUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for controllers: handles CORS, preflight, route dispatching,
 * error mapping and JSON serialization once, so subclasses only declare routes.
 * <p>
 * Subclasses register their routes in the constructor:
 * <pre>
 * get("/api/workflows", ctx -&gt; workflowService.getAllWorkflows());
 * get("/api/workflows/:id", ctx -&gt; workflowService.getWorkflowById(ctx.param("id")));
 * post("/api/workflows", ctx -&gt; HttpResult.created(
 *         workflowService.createWorkflow(ctx.body(WorkflowRequest.class))));
 * </pre>
 * Routes are matched in registration order, so register more specific patterns
 * (e.g. "/api/workflows/key/:key") before catch-all ones (e.g. "/api/workflows/:id").
 */
public abstract class BaseController implements HttpHandler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<Route> routes = new ArrayList<>();
    private volatile String allowedMethods = "OPTIONS";

    protected void get(String pattern, RouteHandler handler) {
        route("GET", pattern, handler, true);
    }

    protected void post(String pattern, RouteHandler handler) {
        route("POST", pattern, handler, true);
    }

    protected void put(String pattern, RouteHandler handler) {
        route("PUT", pattern, handler, true);
    }

    protected void patch(String pattern, RouteHandler handler) {
        route("PATCH", pattern, handler, true);
    }

    protected void delete(String pattern, RouteHandler handler) {
        route("DELETE", pattern, handler, true);
    }

    /** Registers a route that does not require a valid Authorization Bearer token, e.g. login. */
    protected void postPublic(String pattern, RouteHandler handler) {
        route("POST", pattern, handler, false);
    }

    protected void route(String method, String pattern, RouteHandler handler, boolean requiresAuth) {
        routes.add(new Route(method, pattern, handler, requiresAuth));
        allowedMethods = buildAllowedMethods();
        logger.debug("Route registered: {} {} (auth={})", method, pattern, requiresAuth);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        responseHeaders.set("Access-Control-Allow-Methods", allowedMethods);
        responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        logger.info("Incoming HTTP Request: {} {}", method, path);

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            String[] segments = Route.split(path);
            boolean pathMatched = false;

            for (Route route : routes) {
                Map<String, String> pathParams = route.match(segments);
                if (pathParams == null) {
                    continue;
                }
                pathMatched = true;
                if (!route.matchesMethod(method)) {
                    continue;
                }

                RequestContext ctx = new RequestContext(exchange, pathParams);
                if (route.requiresAuth() && !authenticate(exchange, ctx)) {
                    return;
                }

                Object result = route.handler().handle(ctx);
                sendResult(exchange, result);
                return;
            }

            if (pathMatched) {
                sendJsonResponse(exchange, 405, ApiResponse.fail("405", "Method Not Allowed"));
            } else {
                sendJsonResponse(exchange, 404, ApiResponse.fail("404", "Endpoint Not Found"));
            }
        } catch (AppException e) {
            logger.warn("AppException occurred: {}", e.getMessage());
            sendJsonResponse(exchange, e.getStatusCode(), ApiResponse.fail(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal Server Error", e);
            sendJsonResponse(exchange, 500, ApiResponse.fail("500", "Internal Server Error: " + e.getMessage()));
        }
    }

    /**
     * Validates the "Authorization: Bearer &lt;token&gt;" header and, on success,
     * populates {@code ctx} with the caller's identity.
     *
     * @return {@code true} when authenticated (caller should proceed); on
     *         {@code false} a 401 response has already been sent.
     */
    private boolean authenticate(HttpExchange exchange, RequestContext ctx) throws IOException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendJsonResponse(exchange, 401, ApiResponse.fail("401", "Missing or invalid Authorization header"));
            return false;
        }

        String token = header.substring("Bearer ".length()).trim();
        try {
            Claims claims = JwtUtil.parseToken(token);
            ctx.setAuth(claims.getSubject(), claims.get("username", String.class), claims.get("role", String.class));
            return true;
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 401, ApiResponse.fail("401", "Invalid or expired token"));
            return false;
        }
    }

    private void sendResult(HttpExchange exchange, Object result) throws IOException {
        if (result instanceof HttpResult httpResult) {
            if (httpResult.body() == null && httpResult.status() == 204) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            sendJsonResponse(exchange, httpResult.status(), ApiResponse.ok(httpResult.body()));
            return;
        }
        sendJsonResponse(exchange, 200, ApiResponse.ok(result));
    }

    protected void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String jsonResponse = JsonUtil.toJson(data);
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private String buildAllowedMethods() {
        Set<String> methods = new LinkedHashSet<>();
        for (Route route : routes) {
            methods.add(route.method());
        }
        methods.add("OPTIONS");
        return String.join(", ", methods);
    }
}
