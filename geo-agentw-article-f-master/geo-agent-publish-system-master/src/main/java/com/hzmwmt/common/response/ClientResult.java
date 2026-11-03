package com.hzmwmt.common.response;

import lombok.Data;

@Data
public class ClientResult<T> {

    /**
     * 状态码
     */
    public Integer code;

    /**
     * 消息
     */
    public String msg;

    /**
     * 数据
     */
    public T data;

    /**
     * 成功返回带数据
     */
    public static <T> ClientResult<T> success(T data){
        ClientResult<T> result = new ClientResult();
        result.setCode(200);
        result.setMsg("操作成功！");
        result.setData(data);
        return result;
    }

    /**
     * 成功返回不带数据
     */
    public static ClientResult<Void> success(){return success(null);}

    /**
     * 失败返回
     */
    public static <T>ClientResult<T> error(String msg){
        ClientResult<T> result = new ClientResult();
        result.setCode(500);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }
}
