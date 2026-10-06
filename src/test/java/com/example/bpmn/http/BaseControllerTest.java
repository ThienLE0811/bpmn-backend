package com.example.bpmn.http;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.util.JsonUtil;
import com.example.bpmn.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the request pipeline in {@link BaseController}: CORS/preflight, route dispatch,
 * Bearer-token authentication, handler result mapping and exception-to-status mapping.
 */
class BaseControllerTest {

    private static final String VALID_TOKEN = JwtUtil.generateToken("u-1", "alice", "ADMIN");

    private TestHttpServer server;

    /**
     * Mirrors the shape of the real controllers: a mix of public and protected routes, literal
     * and parameterised patterns, and handlers returning plain objects, {@link HttpResult}s,
     * {@code null} and exceptions.
     */
    private static final class TestController extends BaseController {
        TestController() {
            get("/api/things", ctx -> List.of("a", "b"));
            post("/api/things", ctx -> HttpResult.created(Map.of("id", "new-id")));
            // Registered before "/api/things/:id/..." on purpose - see routesAreMatchedInRegistrationOrder.
            get("/api/things/key/:key", ctx -> "by-key:" + ctx.param("key"));
            get("/api/things/:id", ctx -> "by-id:" + ctx.param("id"));
            get("/api/things/:id/versions", ctx -> "versions-of:" + ctx.param("id"));
            put("/api/things/:id", ctx -> "updated:" + ctx.param("id"));
            patch("/api/things/:id", ctx -> "patched:" + ctx.param("id"));
            delete("/api/things/:id", ctx -> HttpResult.noContent());
            postPublic("/api/public/echo", ctx -> "public-ok");
            get("/api/whoami", ctx -> ctx.authUserId() + "|" + ctx.authUsername() + "|" + ctx.authRole());
            get("/api/vietnamese", ctx -> "Xin chào, khoản vay đã được duyệt");
            get("/api/nothing", ctx -> null);
            get("/api/conflict", ctx -> {
                throw new AppException("THING_EXISTS", "Thing already exists", 409);
            });
            get("/api/crash", ctx -> {
                throw new IllegalStateException("kaboom");
            });
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        server = new TestHttpServer(new TestController());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    // --- Authentication --------------------------------------------------------------------

    @Test
    @DisplayName("a public route needs no Authorization header")
    void publicRouteNeedsNoToken() {
        HttpResponse<String> response = server.post("/api/public/echo", "{}");

        assertEquals(200, response.statusCode());
        assertEquals("public-ok", data(response));
    }

    @Test
    @DisplayName("a protected route without an Authorization header is 401")
    void protectedRouteWithoutHeaderIsUnauthorized() {
        HttpResponse<String> response = server.get("/api/things");

        assertEquals(401, response.statusCode());
        assertEquals("401", errors(response).get("errorCode"));
        assertEquals("Missing or invalid Authorization header", errors(response).get("message"));
    }

    @Test
    @DisplayName("an Authorization header that is not a Bearer token is 401")
    void nonBearerAuthorizationIsUnauthorized() {
        HttpResponse<String> response = server.send("GET", "/api/things", null, "Authorization", "Basic dXNlcjpwYXNz");

        assertEquals(401, response.statusCode());
        assertEquals("Missing or invalid Authorization header", errors(response).get("message"));
    }

    @Test
    @DisplayName("a malformed token is 401, not 500")
    void malformedTokenIsUnauthorized() {
        HttpResponse<String> response = server.get("/api/things", "not-a-jwt");

        assertEquals(401, response.statusCode());
        assertEquals("Invalid or expired token", errors(response).get("message"));
    }

    @Test
    @DisplayName("a token signed with another secret is 401")
    void foreignlySignedTokenIsUnauthorized() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-long-enough-for-hmac-sha256-signing".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("u-1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        HttpResponse<String> response = server.get("/api/things", token);

        assertEquals(401, response.statusCode());
        assertEquals("Invalid or expired token", errors(response).get("message"));
    }

    @Test
    @DisplayName("an expired token is 401")
    void expiredTokenIsUnauthorized() {
        String secret = AppConfig.getProperty("jwt.secret",
                "dev-only-insecure-secret-please-override-via-JWT_SECRET-env-var");
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject("u-1")
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(key)
                .compact();

        HttpResponse<String> response = server.get("/api/things", token);

        assertEquals(401, response.statusCode());
        assertEquals("Invalid or expired token", errors(response).get("message"));
    }

    @Test
    @DisplayName("a valid token puts the caller's identity on the RequestContext")
    void validTokenPopulatesCallerIdentity() {
        HttpResponse<String> response = server.get("/api/whoami", VALID_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals("u-1|alice|ADMIN", data(response));
    }

    // --- Route dispatch --------------------------------------------------------------------

    @Test
    @DisplayName("an unknown path under the context is 404 Endpoint Not Found")
    void unknownPathIsNotFound() {
        HttpResponse<String> response = server.get("/api/does-not-exist", VALID_TOKEN);

        assertEquals(404, response.statusCode());
        assertEquals("404", errors(response).get("errorCode"));
        assertEquals("Endpoint Not Found", errors(response).get("message"));
    }

    @Test
    @DisplayName("a known path with an unregistered method is 405, not 404")
    void knownPathWithWrongMethodIsMethodNotAllowed() {
        HttpResponse<String> response = server.send("DELETE", "/api/things", null,
                "Authorization", "Bearer " + VALID_TOKEN);

        assertEquals(405, response.statusCode());
        assertEquals("405", errors(response).get("errorCode"));
        assertEquals("Method Not Allowed", errors(response).get("message"));
    }

    @Test
    @DisplayName("the 401 check runs before the handler, so an unauthenticated call never reaches it")
    void authenticationHappensBeforeDispatch() {
        HttpResponse<String> response = server.get("/api/crash");

        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("routes are matched in registration order, so specific patterns must be registered first")
    void routesAreMatchedInRegistrationOrder() {
        assertEquals("by-key:ORDER", data(server.get("/api/things/key/ORDER", VALID_TOKEN)));
        assertEquals("by-id:abc", data(server.get("/api/things/abc", VALID_TOKEN)));
        assertEquals("versions-of:abc", data(server.get("/api/things/abc/versions", VALID_TOKEN)));
    }

    @Test
    @DisplayName("each registered verb reaches its own handler on the same pattern")
    void everyVerbReachesItsHandler() {
        assertEquals("updated:abc", data(server.send("PUT", "/api/things/abc", "{}",
                "Authorization", "Bearer " + VALID_TOKEN)));
        assertEquals("patched:abc", data(server.send("PATCH", "/api/things/abc", "{}",
                "Authorization", "Bearer " + VALID_TOKEN)));
    }

    @Test
    @DisplayName("a trailing slash still matches the route")
    void trailingSlashStillMatches() {
        HttpResponse<String> response = server.get("/api/things/", VALID_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals(List.of("a", "b"), data(response));
    }

    // --- Result and error mapping ----------------------------------------------------------

    @Test
    @DisplayName("a plain handler result is wrapped in a 200 ApiResponse")
    void plainResultIsWrappedInOk() {
        HttpResponse<String> response = server.get("/api/things", VALID_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals("OK", json(response).get("status"));
        assertEquals(List.of("a", "b"), data(response));
    }

    @Test
    @DisplayName("HttpResult.created maps to 201 with the payload")
    void createdResultMapsTo201() {
        HttpResponse<String> response = server.send("POST", "/api/things", "{}",
                "Authorization", "Bearer " + VALID_TOKEN, "Content-Type", "application/json");

        assertEquals(201, response.statusCode());
        assertEquals(Map.of("id", "new-id"), data(response));
    }

    @Test
    @DisplayName("HttpResult.noContent maps to 204 with an empty body")
    void noContentResultMapsTo204() {
        HttpResponse<String> response = server.send("DELETE", "/api/things/abc", null,
                "Authorization", "Bearer " + VALID_TOKEN);

        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    @DisplayName("a null handler result is still a 200 OK, just without a data field")
    void nullResultIsOkWithoutData() {
        HttpResponse<String> response = server.get("/api/nothing", VALID_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals(Map.of("status", "OK"), json(response));
    }

    @Test
    @DisplayName("AppException maps to its own status code and error code")
    void appExceptionMapsToItsStatus() {
        HttpResponse<String> response = server.get("/api/conflict", VALID_TOKEN);

        assertEquals(409, response.statusCode());
        assertEquals("FAIL", json(response).get("status"));
        assertEquals("THING_EXISTS", errors(response).get("errorCode"));
        assertEquals("Thing already exists", errors(response).get("message"));
    }

    @Test
    @DisplayName("an unexpected exception maps to 500 instead of killing the connection")
    void unexpectedExceptionMapsTo500() {
        HttpResponse<String> response = server.get("/api/crash", VALID_TOKEN);

        assertEquals(500, response.statusCode());
        assertEquals("500", errors(response).get("errorCode"));
        assertTrue(((String) errors(response).get("message")).startsWith("Internal Server Error"),
                "got: " + errors(response).get("message"));
    }

    @Test
    @DisplayName("responses are UTF-8 JSON, so Vietnamese text survives intact")
    void responsesAreUtf8Json() {
        HttpResponse<String> response = server.get("/api/vietnamese", VALID_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=UTF-8",
                response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("Xin chào, khoản vay đã được duyệt", data(response));
    }

    // --- CORS ------------------------------------------------------------------------------

    @Test
    @DisplayName("an OPTIONS preflight is answered with 204 and the CORS headers")
    void optionsPreflightIsNoContent() {
        HttpResponse<String> response = server.send("OPTIONS", "/api/things", null);

        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("Content-Type, Authorization",
                response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow());
    }

    @Test
    @DisplayName("Access-Control-Allow-Methods lists every registered verb plus OPTIONS")
    void allowedMethodsListsRegisteredVerbs() {
        HttpResponse<String> response = server.send("OPTIONS", "/api/things", null);

        String allowed = response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow();
        assertEquals("GET, POST, PUT, PATCH, DELETE, OPTIONS", allowed);
    }

    @Test
    @DisplayName("ordinary responses carry the CORS origin header too")
    void ordinaryResponsesCarryCorsHeader() {
        HttpResponse<String> response = server.get("/api/things", VALID_TOKEN);

        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    // --- Helpers ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(HttpResponse<String> response) {
        return JsonUtil.fromJson(response.body(), Map.class);
    }

    private static Object data(HttpResponse<String> response) {
        return json(response).get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errors(HttpResponse<String> response) {
        return (Map<String, Object>) json(response).get("errors");
    }
}
