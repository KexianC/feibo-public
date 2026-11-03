package com.hzmwmt.types.enums;

public enum LoginType {
    FRONTEND(1, "前台登录"),
    BACKEND(2, "后台登录");

    private final int code;
    private final String description;

    LoginType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static LoginType getByCode(Integer code) {
        if (code == null) return FRONTEND;
        for (LoginType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return FRONTEND;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}