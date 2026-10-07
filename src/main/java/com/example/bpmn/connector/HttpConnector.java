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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><strong>Which hosts may be called is a deployment decision, not a modelling one.</strong>
 * Anyone able to edit a BPMN diagram can otherwise make the server issue requests from inside
 * the network - to a cloud metadata endpoint, an internal admin API, or a database's HTTP
 * front-end - and read the reply back out through a process variable. So the host must appear in
 * {@code connector.http.allowed-hosts}, which is empty (meaning: nothing is allowed) until an
 * operator sets it, and redirects are never followed, so a permitted host cannot bounce the
 * request onwards to a forbidden one. The residual gap is DNS: an allowed name whose record is
 * changed to point at an internal address still passes, since the check is on the name.
 */
public class HttpConnector implements Connector {
    public static final String ID = "http";

    private static final Logger logger = LoggerFactory.getLogger(HttpConnector.class);
    private static final String HEADER_INPUT_PREFIX = "header.";
    private static final String WILDCARD_ALL = "*";
    private static final String SUBDOMAIN_WILDCARD_PREFIX = "*.";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final Duration requestTimeout;
    private final List<String> allowedHosts;

    public HttpConnector() {
        this(timeoutProperty("connector.http.connect-timeout-seconds", DEFAULT_CONNECT_TIMEOUT),
                timeoutProperty("connector.http.request-timeout-seconds", DEFAULT_REQUEST_TIMEOUT),
                hostsProperty("connector.http.allowed-hosts"));
    }

    /** @param allowedHosts exact host names, {@code *.suffix} patterns, or a single {@code *} to switch the check off. */
    public HttpConnector(Duration connectTimeout, Duration requestTimeout, List<String> allowedHosts) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // A permitted host must not be able to redirect the request on to a forbidden one.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .toList();

        if (this.allowedHosts.contains(WILDCARD_ALL)) {
            logger.warn("connector.http.allowed-hosts is \"{}\": service tasks may call any address, "
                    + "including internal ones. Set an explicit host list outside development.", WILDCARD_ALL);
        } else if (this.allowedHosts.isEmpty()) {
            logger.info("connector.http.allowed-hosts is empty - the http connector will refuse every call "
                    + "until hosts are listed.");
        }
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

    /** Parses the url and checks it against the deployment's host policy before anything is sent. */
    private URI uri(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new AppException("Input \"url\" is not a valid URL: " + url, 400);
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new AppException("Input \"url\" must be http or https, got: " + url, 400);
        }
        if (uri.getHost() == null) {
            throw new AppException("Input \"url\" has no host: " + url, 400);
        }
        if (!isHostAllowed(uri.getHost())) {
            throw new AppException("Host \"" + uri.getHost() + "\" is not in connector.http.allowed-hosts,"
                    + " so the http connector refused to call it", 403);
        }
        return uri;
    }

    /** Package-private so the host policy can be tested on its own, without a network call to decide it. */
    boolean isHostAllowed(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            if (allowed.equals(WILDCARD_ALL) || allowed.equals(normalized)) {
                return true;
            }
            // "*.example.com" covers sub-domains only, not "example.com" itself - listing the
            // bare domain as well is an explicit decision rather than an accident of matching.
            if (allowed.startsWith(SUBDOMAIN_WILDCARD_PREFIX)
                    && normalized.endsWith(allowed.substring(WILDCARD_ALL.length()))) {
                return true;
            }
        }
        return false;
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

    /** Reads a comma-separated host list; absent or blank means no host is allowed. */
    private static List<String> hostsProperty(String key) {
        String configured = AppConfig.getProperty(key);
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configured.split(",")).map(String::trim).filter(host -> !host.isEmpty()).toList();
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
