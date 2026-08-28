package com.example.bpmn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private String errorCode;
    private String message;

    public ApiError() {
        this.errorCode = "00";
        this.message = "";
    }

    public ApiError(String errorCode, String message) {
        this.errorCode = (errorCode != null && !errorCode.isBlank()) ? errorCode : "00";
        this.message = (message != null) ? message : "";
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = (errorCode != null && !errorCode.isBlank()) ? errorCode : "00";
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = (message != null) ? message : "";
    }
}
