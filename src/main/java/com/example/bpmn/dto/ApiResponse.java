package com.example.bpmn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard API Response Wrapper
 * Format:
 * Success: { "status": "OK", "data": ... }
 * Failure: { "status": "FAIL", "errors": { "errorCode": "...", "message": "..." } }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private String status;
    private T data;
    private ApiError errors;

    public ApiResponse() {
    }

    public ApiResponse(String status, T data, ApiError errors) {
        this.status = status;
        this.data = data;
        this.errors = errors;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>("FAIL", null, new ApiError("00", message));
    }

    public static <T> ApiResponse<T> fail(String errorCode, String message) {
        return new ApiResponse<>("FAIL", null, new ApiError(errorCode, message));
    }

    public static <T> ApiResponse<T> fail(ApiError errors) {
        return new ApiResponse<>("FAIL", null, errors);
    }

    public static <T> ApiResponse<T> fail(ApiError errors, T data) {
        return new ApiResponse<>("FAIL", data, errors);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ApiError getErrors() {
        return errors;
    }

    public void setErrors(ApiError errors) {
        this.errors = errors;
    }
}
