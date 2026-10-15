package com.gymvip.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {
    private LocalDateTime timestamp = LocalDateTime.now();
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private String correlationId;
    private List<FieldErrorDetail> details;

    public ErrorResponse() {}

    public ErrorResponse(int status, String error, String code, String message, String path, String correlationId) {
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.path = path;
        this.correlationId = correlationId;
    }

    public static class FieldErrorDetail {
        private String field;
        private String message;

        public FieldErrorDetail(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getCorrelationId() { return correlationId; }
    public List<FieldErrorDetail> getDetails() { return details; }
    public void setDetails(List<FieldErrorDetail> details) { this.details = details; }
}
