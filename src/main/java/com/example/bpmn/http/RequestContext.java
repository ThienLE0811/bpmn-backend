package com.example.bpmn.http;

import com.example.bpmn.exception.AppException;
import com.example.bpmn.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything a route handler needs from the incoming request:
 * path parameters, query parameters and the JSON body.
 */
public class RequestContext {

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private Map<String, String> queryParams;
    private String authUserId;
    private String authUsername;
    private String authRole;

    RequestContext(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = pathParams;
    }

    /** Populated by {@link BaseController} after successfully validating the request's JWT. */
    void setAuth(String userId, String username, String role) {
        this.authUserId = userId;
        this.authUsername = username;
        this.authRole = role;
    }

    /** ID of the authenticated user, or {@code null} on routes that don't require auth. */
    public String authUserId() {
        return authUserId;
    }

    /** Username of the authenticated user, or {@code null} on routes that don't require auth. */
    public String authUsername() {
        return authUsername;
    }

    /** Role of the authenticated user, or {@code null} on routes that don't require auth. */
    public String authRole() {
        return authRole;
    }

    /**
     * Path parameter declared in the route pattern, e.g. ":id" in "/api/workflows/:id".
     * Fails with 500 when the name is not declared in the pattern (a coding mistake).
     */
    public String param(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new AppException("Route pattern has no path parameter named ':" + name + "'", 500);
        }
        return value;
    }

    /** Query string parameter, or {@code null} when absent. e.g. "?page=2" */
    public String query(String name) {
        return queryParams().get(name);
    }

    public String query(String name, String defaultValue) {
        String value = queryParams().get(name);
        return value != null ? value : defaultValue;
    }

    /** 1-based page number from "?page=", clamped to at least 1 (defaults to 1). */
    public int pageParam() {
        return Math.max(1, parseIntOrDefault(query("page"), 1));
    }

    /** Page size from "?size=", clamped to [1, 100] (defaults to 20). */
    public int sizeParam() {
        int size = parseIntOrDefault(query("size"), 20);
        return Math.min(100, Math.max(1, size));
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Parse the JSON request body into the given type. */
    public <T> T body(Class<T> type) {
        String raw = rawBody();
        if (raw.isBlank()) {
            throw new AppException("Request body must not be empty", 400);
        }
        try {
            return JsonUtil.fromJson(raw, type);
        } catch (RuntimeException e) {
            throw new AppException("Invalid JSON request body", 400);
        }
    }

    public String rawBody() {
        try {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AppException("Unable to read request body", 400);
        }
    }

    /** Escape hatch for the rare case a handler needs the raw exchange (headers, etc.). */
    public HttpExchange exchange() {
        return exchange;
    }

    private Map<String, String> queryParams() {
        if (queryParams == null) {
            queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
        }
        return queryParams;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }
        Map<String, String> params = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            if (separator < 0) {
                params.put(decode(pair), "");
            } else {
                params.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
            }
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
