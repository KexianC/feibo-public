package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName User
 * @Date: 2025/9/3 17:14
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_user")
public class User {

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID (雪花算法生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 密码
     */
    private String password;

    /**
     * 手机号码
     */
    private String phoneNumber;

    /**
     * 性别 (0:未知, 1:男, 2:女)
     */
    private Integer gender;

    /**
     * 状态 (0:正常, 1:禁用)
     */
    private Boolean status;

    /**
     * 微信ID (或其他第三方登录ID)
     */
    private String openId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
