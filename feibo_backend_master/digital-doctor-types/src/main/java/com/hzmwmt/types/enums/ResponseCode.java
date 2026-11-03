package com.hzmwmt.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author huihui
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    RUNTIME_EXCEPTION("0002", "运行异常"),
    ILLEGAL_PARAMETER("0003", "非法参数"),
    PARAM_ERROR("0018", "参数错误"),
    INDEX_EXCEPTION("0004", "唯一索引冲突"),
    UPDATE_ZERO("0005", "更新记录为0"),
    HTTP_EXCEPTION("0006", "HTTP接口调用异常"),
    SERVICE_ERROR("0007", "系统服务异常"),
    REQUEST_METHOD_NOT_SUPPORTED("0008", "不支持的请求方法"),
    TOKEN_EXPIRED("0009", "token过期"),
    MANAGE_ERROR("0010", "后台配置错误，请联系客服"),
    INTERNET_EXCEPTION("0011", "网络异常"),
    REPEATED_OPERATION_EXCEPTION("0012", "重复操作异常"),
    NO_PAY_ORDER_EXCEPTION("0013", "当前存在未支付订单"),
    REFRESH_TOKEN_EXCEPTION("0014", "刷新token已注销"),
    REMOTE_REQUEST_EXCEPTION("0015", "第三方请求失败"),
    AUTHORIZATION_EXCEPTION("0016", "授权失败"),
    RESOURCE_QUOTA_ANOMALY("0017", "资源额度不足"),
    RESOURCE_NOT_FOUND("0019", "请求资源不存在"),
    VOICE_REQUIRED("DH001", "尚未选择配音，请选择配音后重新合成"),
    VOICE_INVALID("DH002", "所选配音无效，请重新选择配音后合成"),
    UPLOAD_EXCEPTION("5001", "文件上传异常"),
    DELETE_EXCEPTION("5002", "文件删除异常"),

    E0001("E0001", "用户当前未登录"),
    E0003("E0003", "用户号和密码不匹配"),
    E0004("E0004", "账号重复"),
    E0005("E0005", "用户不存在"),
    E0006("E0006", "用户状态异常"),
    E0007("E0007", "用户角色未配置"),
    E008("E0008", "审批冲突"),
    E009("E0009", "驳回冲突"),
    E010("E0010", "任务已被领取"),
    E011("E0011", "操作不被允许"),
    SYSTEM_ERROR("E0012", "系统错误"),
    FORBIDDEN("E0013", "权限不足"),
    UNAUTHORIZED("E0014", "未授权"),
    MFA_REQUIRED("E0015", "需要二次验证");


    private String code;
    private String info;

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }
}


