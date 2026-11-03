package com.hzmwmt.domain.common;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用API响应结果封装
 * 所有接口统一返回此对象，前端根据 code 判断请求状态
 */
@Data
public class Result<T> implements Serializable {

    public static final int PARAM_ERROR = 1;
    public static final int OPERATION_FAILED = 2;
    /**
     * 状态码：200=成功，其他=失败（可根据业务定义更多状态码）
     */
    private Integer code;

    /**
     * 提示信息（成功/失败原因）
     */
    private String msg;

    /**
     * 响应数据（成功时返回业务数据，失败时可返回null或错误详情）
     */
    private T data;

    /**
     * 响应时间戳（可选，便于排查问题）
     */
    private LocalDateTime timestamp;


    // 构造方法私有化，通过静态方法创建实例
    private Result() {
        this.timestamp = LocalDateTime.now(); // 自动填充当前时间
    }


    // -------------------------- 成功响应 --------------------------

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(null);
        return result;
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（仅自定义提示信息，无数据）
     */
    public static <T> Result<T> success(String msg) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);  // 用传入的msg
        result.setData(null); // data为null
        return result;
    }

    /**
     * 成功响应（自定义提示信息+数据）
     */
    public static <T> Result<T> success(String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }


    // -------------------------- 失败响应 --------------------------

    /**
     * 失败响应（默认提示）
     */
    public static <T> Result<T> error() {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg("操作失败");
        result.setData(null);
        return result;
    }

    /**
     * 失败响应（自定义错误信息）
     */
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }

    /**
     * 失败响应（自定义状态码+错误信息）
     */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }

    /**
     * 失败响应（带错误详情数据）
     */
    public static <T> Result<T> error(String msg, T errorData) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg(msg);
        result.setData(errorData); // 可返回错误详情（如参数校验失败的字段）
        return result;
    }
}