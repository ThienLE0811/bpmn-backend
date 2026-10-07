package com.example.bpmn.connector;

import com.example.bpmn.config.AppConfig;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Calls an external HTTP API.
 *
 * <p>Inputs: {@code url} (required), {@code method} (default {@code GET}), {@code body},
 * {@code contentType} (default {@code application/json} when there is a body),
 * {@code failOnError} (default true), and any input named {@code header.X}, which is sent as the
 * request header {@code X}. A {@code body} that is a map or list is JSON-encoded.
 *
 * <p>Outputs: {@code statusCode} (int), {@code body} (the raw response text) and {@code json}
 * (the response parsed into a map/list, or null when it is not JSON). A service task reads these
 * with output parameters, e.g. {@code <camunda:outputParameter name="orderId">${json.id}</...>}.
 *
 * <p>Timeouts are always set - this runs inside the request that started or advanced the process
 * instance, so an API that never answers would otherwise hang that caller indefinitely. By
 * default a non-2xx response fails the call (and so raises an incident on the service task);
 * set {@code failOnError} to false to let the model inspect {@code statusCode} instead.
 */
public class HttpConnector implements Connector {
    public static final String ID = "http";

    private static final Logger logger = LoggerFactory.getLogger(HttpConnector.class);
    private static final String HEADER_INPUT_PREFIX = "header.";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpConnector() {
        this(timeoutProperty("connector.http.connect-timeout-seconds", DEFAULT_CONNECT_TIMEOUT),
                timeoutProperty("connector.http.request-timeout-seconds", DEFAULT_REQUEST_TIMEOUT));
    }

    public HttpConnector(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs) {
        String url = requiredString(inputs, "url");
        String method = optionalString(inputs, "method", "GET").toUpperCase(Locale.ROOT);
        String body = encodeBody(inputs.get("body"));
        boolean failOnError = optionalBoolean(inputs, "failOnError", true);

        HttpRequest.Builder request = HttpRequest.newBuilder(uri(url))
                .timeout(requestTimeout)
                .method(method, body != null
                        ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                        : HttpRequest.BodyPublishers.noBody());

        if (body != null) {
            request.header("Content-Type", optionalString(inputs, "contentType", "application/json"));
        }
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            if (input.getKey().startsWith(HEADER_INPUT_PREFIX) && input.getValue() != null) {
                request.header(input.getKey().substring(HEADER_INPUT_PREFIX.length()), String.valueOf(input.getValue()));
            }
        }

        HttpResponse<String> response = send(request.build(), method, url);
        logger.info("Connector {} {} responded {}", method, url, response.statusCode());

        if (failOnError && (response.statusCode() < 200 || response.statusCode() >= 300)) {
            throw new AppException(method + " " + url + " returned HTTP " + response.statusCode()
                    + ": " + abbreviate(response.body()), 502);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCode", response.statusCode());
        result.put("body", response.body());
        result.put("json", parseJsonOrNull(response.body()));
        return result;
    }

    private HttpResponse<String> send(HttpRequest request, String method, String url) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException("Interrupted while calling " + method + " " + url, e, 500);
        } catch (Exception e) {
            throw new AppException("Could not call " + method + " " + url + ": " + e, e, 502);
        }
    }

    /** Maps and lists are sent as JSON; anything else is sent as its text form. */
    private static String encodeBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof Map<?, ?> || body instanceof Iterable<?>) {
            return JsonUtil.toJson(body);
        }
        return String.valueOf(body);
    }

    /** Best-effort: a non-JSON response is not an error, the model can still read {@code body}. */
    private static Object parseJsonOrNull(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonUtil.fromJson(body, Object.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static URI uri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new AppException("Input \"url\" is not a valid URL: " + url, 400);
        }
    }

    private static String requiredString(Map<String, Object> inputs, String name) {
        Object value = inputs.get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new AppException("Input \"" + name + "\" is required by the http connector", 400);
        }
        return String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> inputs, String name, String defaultValue) {
        Object value = inputs.get(name);
        return (value == null || String.valueOf(value).isBlank()) ? defaultValue : String.valueOf(value).trim();
    }

    private static boolean optionalBoolean(Map<String, Object> inputs, String name, boolean defaultValue) {
        Object value = inputs.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }

    private static Duration timeoutProperty(String key, Duration defaultValue) {
        String configured = AppConfig.getProperty(key);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(configured.trim()));
        } catch (NumberFormatException e) {
            logger.warn("Ignoring non-numeric {}=\"{}\", using {}", key, configured, defaultValue);
            return defaultValue;
        }
    }
}
