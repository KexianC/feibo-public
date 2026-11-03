package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalModel
 * @Date: 2025/9/12 23:51
 */
@Data
@Builder
@TableName("t_digital_model")
public class DigitalModel {
    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalModelId;

    /**
     * 声音ID
     */
    private Long voiceId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 名称
     */
    private String name;

    /**
     * 形象ID
     */
    private Long imageId;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 性别: 0-男, 1-女
     */
    private Boolean gender;

    /**
     * 状态: 0-上架, 1-下架
     */
    private Boolean status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
