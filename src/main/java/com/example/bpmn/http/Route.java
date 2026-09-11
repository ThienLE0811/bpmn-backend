package com.example.bpmn.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single registered route: HTTP method + path pattern + handler.
 * <p>
 * Pattern segments starting with ':' are path parameters,
 * e.g. "/api/workflows/:id" matches "/api/workflows/abc" with id = "abc".
 */
class Route {
    private final String method;
    private final String pattern;
    private final String[] patternSegments;
    private final RouteHandler handler;
    private final boolean requiresAuth;

    Route(String method, String pattern, RouteHandler handler, boolean requiresAuth) {
        this.method = method;
        this.pattern = pattern;
        this.patternSegments = split(pattern);
        this.handler = handler;
        this.requiresAuth = requiresAuth;
    }

    /**
     * Split a path into non-empty segments, so that "/api/users", "/api/users/"
     * and "//api//users" all yield ["api", "users"].
     */
    static String[] split(String path) {
        if (path == null || path.isEmpty()) {
            return new String[0];
        }
        List<String> segments = new ArrayList<>(4);
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments.toArray(new String[0]);
    }

    /**
     * @return the captured path parameters when the path matches (possibly empty),
     *         or {@code null} when it does not match.
     */
    Map<String, String> match(String[] segments) {
        if (segments.length != patternSegments.length) {
            return null;
        }
        Map<String, String> params = null;
        for (int i = 0; i < patternSegments.length; i++) {
            String expected = patternSegments[i];
            if (expected.startsWith(":")) {
                if (params == null) {
                    params = new HashMap<>(4);
                }
                params.put(expected.substring(1), segments[i]);
            } else if (!expected.equals(segments[i])) {
                return null;
            }
        }
        return params != null ? params : Map.of();
    }

    boolean matchesMethod(String requestMethod) {
        return method.equalsIgnoreCase(requestMethod);
    }

    boolean requiresAuth() {
        return requiresAuth;
    }

    String method() {
        return method;
    }

    String pattern() {
        return pattern;
    }

    RouteHandler handler() {
        return handler;
    }
}
