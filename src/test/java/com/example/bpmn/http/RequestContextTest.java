package com.example.bpmn.http;

import com.example.bpmn.util.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what {@link RequestContext} gives a route handler: path parameters, query parameters,
 * pagination clamping and JSON body parsing. Driven over a real HTTP request so the decoding
 * done by {@code HttpExchange} is part of what is tested.
 */
class RequestContextTest {

    private TestHttpServer server;

    /** All routes are registered as public so these tests stay about RequestContext only. */
    private static final class TestController extends BaseController {
        TestController() {
            route("GET", "/api/param/:id", ctx -> ctx.param("id"), false);
            route("GET", "/api/param-undeclared/:id", ctx -> ctx.param("other"), false);
            route("GET", "/api/query", ctx ->
                    String.valueOf(ctx.query("name")) + "|" + ctx.query("missing", "fallback"), false);
            route("GET", "/api/page", ctx -> ctx.pageParam() + "/" + ctx.sizeParam(), false);
            route("POST", "/api/body", ctx -> ctx.body(SampleRequest.class).getName(), false);
            route("POST", "/api/raw", ctx -> ctx.rawBody(), false);
        }
    }

    public static class SampleRequest {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

    // --- Path parameters -------------------------------------------------------------------

    @Test
    @DisplayName("param returns the captured segment")
    void paramReturnsCapturedSegment() {
        assertEquals("abc-123", data(server.get("/api/param/abc-123")));
    }

    @Test
    @DisplayName("a percent-encoded path parameter arrives decoded")
    void percentEncodedParamIsDecoded() {
        assertEquals("a b", data(server.get("/api/param/a%20b")));
        assertEquals("Lê", data(server.get("/api/param/L%C3%AA")));
    }

    @Test
    @DisplayName("asking for a parameter the pattern does not declare is a 500 coding error")
    void undeclaredParamIsServerError() {
        HttpResponse<String> response = server.get("/api/param-undeclared/abc");

        assertEquals(500, response.statusCode());
        assertEquals("Route pattern has no path parameter named ':other'", errors(response).get("message"));
    }

    // --- Query parameters ------------------------------------------------------------------

    @Test
    @DisplayName("query returns the value, or null / the supplied default when absent")
    void queryReturnsValueOrDefault() {
        assertEquals("hello|fallback", data(server.get("/api/query?name=hello")));
        assertEquals("null|fallback", data(server.get("/api/query")));
        assertEquals("hello|given", data(server.get("/api/query?name=hello&missing=given")));
    }

    @Test
    @DisplayName("query values are URL-decoded, including '+' as a space")
    void queryValuesAreDecoded() {
        assertEquals("a b|fallback", data(server.get("/api/query?name=a%20b")));
        assertEquals("a b|fallback", data(server.get("/api/query?name=a+b")));
        assertEquals("Lê Thị|fallback", data(server.get("/api/query?name=L%C3%AA%20Th%E1%BB%8B")));
    }

    @Test
    @DisplayName("a query parameter without '=' reads back as an empty string")
    void valuelessQueryParamIsEmptyString() {
        assertEquals("|fallback", data(server.get("/api/query?name")));
    }

    // --- Pagination ------------------------------------------------------------------------

    @Test
    @DisplayName("pagination defaults to page 1, size 20")
    void paginationDefaults() {
        assertEquals("1/20", data(server.get("/api/page")));
    }

    @Test
    @DisplayName("valid pagination values pass through")
    void paginationPassesThroughValidValues() {
        assertEquals("3/50", data(server.get("/api/page?page=3&size=50")));
        assertEquals("1/100", data(server.get("/api/page?page=1&size=100")));
    }

    @Test
    @DisplayName("page is clamped to at least 1 and size to [1, 100]")
    void paginationIsClamped() {
        assertEquals("1/1", data(server.get("/api/page?page=0&size=0")));
        assertEquals("1/1", data(server.get("/api/page?page=-5&size=-5")));
        assertEquals("1/100", data(server.get("/api/page?page=1&size=1000")));
    }

    @Test
    @DisplayName("non-numeric or blank pagination values fall back to the defaults")
    void paginationFallsBackOnGarbage() {
        assertEquals("1/20", data(server.get("/api/page?page=abc&size=xyz")));
        assertEquals("1/20", data(server.get("/api/page?page=&size=")));
        assertEquals("2/7", data(server.get("/api/page?page=%202%20&size=%207%20")), "values are trimmed");
    }

    // --- Request body ----------------------------------------------------------------------

    @Test
    @DisplayName("a JSON body is parsed into the DTO")
    void jsonBodyIsParsed() {
        assertEquals("Anh", data(server.post("/api/body", "{\"name\":\"Anh\"}")));
    }

    @Test
    @DisplayName("fields the DTO does not declare are ignored, not rejected")
    void unknownBodyFieldsAreIgnored() {
        assertEquals("Anh", data(server.post("/api/body", "{\"name\":\"Anh\",\"createdAt\":\"x\"}")));
    }

    @Test
    @DisplayName("an empty body is a 400, not a 500")
    void emptyBodyIsBadRequest() {
        HttpResponse<String> response = server.post("/api/body", "");

        assertEquals(400, response.statusCode());
        assertEquals("Request body must not be empty", errors(response).get("message"));
    }

    @Test
    @DisplayName("a malformed JSON body is a 400, not a 500")
    void malformedJsonBodyIsBadRequest() {
        HttpResponse<String> response = server.post("/api/body", "{\"name\":");

        assertEquals(400, response.statusCode());
        assertEquals("Invalid JSON request body", errors(response).get("message"));
    }

    @Test
    @DisplayName("rawBody reads the request body as UTF-8")
    void rawBodyIsReadAsUtf8() {
        assertEquals("Khoản vay đã duyệt", data(server.post("/api/raw", "Khoản vay đã duyệt")));
    }

    // --- Helpers -----------------------------------------------------------------------------

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
