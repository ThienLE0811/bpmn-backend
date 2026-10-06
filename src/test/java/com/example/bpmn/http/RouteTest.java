package com.example.bpmn.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests for the path-matching primitives behind {@link BaseController}. */
class RouteTest {

    private static Route route(String method, String pattern) {
        return new Route(method, pattern, ctx -> null, true);
    }

    private static Map<String, String> match(String pattern, String path) {
        return route("GET", pattern).match(Route.split(path));
    }

    @Test
    @DisplayName("split drops empty segments, so leading/trailing/double slashes do not matter")
    void splitNormalisesSlashes() {
        String[] expected = {"api", "users"};

        assertArrayEquals(expected, Route.split("/api/users"));
        assertArrayEquals(expected, Route.split("/api/users/"));
        assertArrayEquals(expected, Route.split("api/users"));
        assertArrayEquals(expected, Route.split("//api//users//"));
    }

    @Test
    @DisplayName("split returns no segments for an empty or root path")
    void splitHandlesEmptyPaths() {
        assertEquals(0, Route.split(null).length);
        assertEquals(0, Route.split("").length);
        assertEquals(0, Route.split("/").length);
    }

    @Test
    @DisplayName("a literal pattern matches only the identical path")
    void literalPatternMatchesExactPath() {
        assertEquals(Map.of(), match("/api/users", "/api/users"));
        assertEquals(Map.of(), match("/api/users", "/api/users/"));
        assertNull(match("/api/users", "/api/user"));
        assertNull(match("/api/users", "/api/users/extra"), "a longer path must not match");
        assertNull(match("/api/users", "/api"), "a shorter path must not match");
    }

    @Test
    @DisplayName("':name' segments are captured as path parameters")
    void parameterSegmentsAreCaptured() {
        assertEquals(Map.of("id", "abc"), match("/api/users/:id", "/api/users/abc"));
        assertEquals(Map.of("id", "7", "version", "2"),
                match("/api/processes/:id/versions/:version", "/api/processes/7/versions/2"));
    }

    @Test
    @DisplayName("a parameter matches any single segment but never spans a slash")
    void parameterMatchesExactlyOneSegment() {
        assertEquals(Map.of("id", "a-long-segment"), match("/api/users/:id", "/api/users/a-long-segment"));
        assertNull(match("/api/users/:id", "/api/users/a/b"));
    }

    @Test
    @DisplayName("a literal segment still has to match when the pattern also has parameters")
    void literalSegmentsStillHaveToMatch() {
        assertEquals(Map.of("id", "7"), match("/api/processes/:id/versions", "/api/processes/7/versions"));
        assertNull(match("/api/processes/:id/versions", "/api/processes/7/history"));
    }

    @Test
    @DisplayName("matchesMethod ignores case and rejects a different verb")
    void matchesMethodIsCaseInsensitive() {
        Route getRoute = route("GET", "/api/users");

        assertTrue(getRoute.matchesMethod("GET"));
        assertTrue(getRoute.matchesMethod("get"));
        assertFalse(getRoute.matchesMethod("POST"));
    }

    @Test
    @DisplayName("the auth flag and pattern are carried through unchanged")
    void routeKeepsItsMetadata() {
        Route publicRoute = new Route("POST", "/api/auth/login", ctx -> null, false);

        assertEquals("POST", publicRoute.method());
        assertEquals("/api/auth/login", publicRoute.pattern());
        assertFalse(publicRoute.requiresAuth());
        assertTrue(route("GET", "/api/users").requiresAuth());
    }
}
