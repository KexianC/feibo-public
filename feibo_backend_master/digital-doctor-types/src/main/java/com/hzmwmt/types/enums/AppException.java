package com.hzmwmt.types.enums;

/**
 * 自定义业务异常
 */
public class AppException extends RuntimeException {
    private final String code;

    public AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
