package com.hzmwmt.domain.entity.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户登录日志实体类
 * 对应数据库表：t_login_log
 */
@Data
@TableName("t_login_log")
public class TLoginLog {

    /**
     * 日志唯一标识，自增主键
     */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Integer logId;

    /**
     * 登录的用户名
     */
    @TableField("login_user")
    private String loginUser;

    /**
     * 登录用户昵称，仅用于查询结果展示，不写入日志表
     */
    @TableField(exist = false)
    private String nickName;

    /**
     * 登录时使用的IP地址
     */
    @TableField("login_ip")
    private String loginIp;

    /**
     * 登录IP对应的地区信息（如：中国-浙江省-杭州市-电信）
     */
    @TableField("login_region")
    private String loginRegion;

    /**
     * 登录设备的User-Agent信息（浏览器/设备标识）
     */
    @TableField("login_user_agent")
    private String loginUserAgent;

    /**
     * 登录状态（如：登录成功、登录失败）
     */
    @TableField("login_status")
    private String loginStatus;

    /**
     * 登录操作发生的时间
     */
    @TableField("login_time")
    private Date loginTime;
}
