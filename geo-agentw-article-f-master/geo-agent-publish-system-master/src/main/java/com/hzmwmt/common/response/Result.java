package com.hzmwmt.common.response;

import lombok.Data;

/**
 * 通用接口返回对象。
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> {

    /**
     * 是否成功。
     */
    private Boolean success;

    /**
     * 业务码。
     */
    private String code;

    /**
     * 返回消息。
     */
    private String message;

    /**
     * 返回数据。
     */
    private T data;

    /**
     * 构建成功结果。
     *
     * @param data 返回数据
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode("SUCCESS");
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 构建无数据成功结果。
     *
     * @return 成功结果
     */
    public static Result<Void> success() {
        return success(null);
    }

    /**
     * 构建失败结果。
     *
     * @param code 业务码
     * @param message 返回消息
     * @param <T> 数据类型
     * @return 失败结果
     */
    public static <T> Result<T> failure(String code, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        return result;
    }
}
