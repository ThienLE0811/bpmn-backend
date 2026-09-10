package com.example.bpmn.http;

/**
 * Handler for a single route.
 * <p>
 * Return the payload to send back to the client. It is wrapped in
 * {@link com.example.bpmn.dto.ApiResponse#ok(Object)} and sent with status 200.
 * Return an {@link HttpResult} instead when a different status code is needed.
 */
@FunctionalInterface
public interface RouteHandler {
    Object handle(RequestContext ctx);
}
