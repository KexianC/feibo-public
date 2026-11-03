package com.hzmwmt.domain.entity.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 操作日志表实体类
 * 对应数据库表：t_operation_logs
 *
 * @author admin
 */
@Data
@TableName("t_operation_logs")
public class TOperationLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志唯一ID
     */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Integer logId;

    /**
     * 操作者
     */
    @TableField("operator")
    private String operator;

    /**
     * 操作者昵称，仅用于查询结果展示，不写入日志表
     */
    @TableField(exist = false)
    private String nickName;

    /**
     * 操作者IP
     */
    @TableField("operator_ip")
    private String operatorIp;

    /**
     * 登录地区
     */
    @TableField("login_region")
    private String loginRegion;

    /**
     * 登录设备头（浏览器/系统信息）
     */
    @TableField("login_device")
    private String loginDevice;

    /**
     * 操作方法（接口/功能标识）
     */
    @TableField("operation_method")
    private String operationMethod;

    /**
     * 操作描述（具体操作内容）
     */
    @TableField("operation_desc")
    private String operationDesc;

    /**
     * 操作时间
     */
    @TableField("operation_time")
    private Date operationTime;

}
