package com.hzmwmt.common.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String code;
    private String message;
    private String path;

    public static ErrorResponse of(int status, String code, String message, String path) {
        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setStatus(status);
        response.setCode(code);
        response.setMessage(message);
        response.setPath(path);
        return response;
    }

}
