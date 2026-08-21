package com.example.bpmn.exception;

public class AppException extends RuntimeException {
    private final int statusCode;

    public AppException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public AppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AppException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
