package com.example.bpmn.http;

/**
 * Wraps a payload together with an explicit HTTP status code.
 * Only needed when a route must answer with something other than 200.
 */
public record HttpResult(int status, Object body) {

    public static HttpResult of(int status, Object body) {
        return new HttpResult(status, body);
    }

    /** 201 Created */
    public static HttpResult created(Object body) {
        return new HttpResult(201, body);
    }

    /** 204 No Content (empty body) */
    public static HttpResult noContent() {
        return new HttpResult(204, null);
    }
}
