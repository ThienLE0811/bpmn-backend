package com.example.bpmn.exception;

public class AppException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;

    public AppException(String message) {
        super(message);
        this.statusCode = 500;
        this.errorCode = "500";
    }

    public AppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = String.valueOf(statusCode);
    }

    public AppException(String errorCode, String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = (errorCode != null && !errorCode.isBlank()) ? errorCode : "00";
    }

    public AppException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = String.valueOf(statusCode);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode != null ? errorCode : "00";
    }
}
