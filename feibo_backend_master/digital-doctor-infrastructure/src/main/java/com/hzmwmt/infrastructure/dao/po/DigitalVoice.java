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
 * @Description: TODO 配音库表实体类
 * @ClassName VoiceLibrary
 * @Date: 2025/8/14 16:07
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_digital_voice")
public class DigitalVoice {
    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID (雪花算法)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalVoiceId;

    /**
     * 配音名称
     */
    private String name;

    /**
     * 配音风格参数 (用于最终调用AI接口)
     */
    private String styleName;

    /**
     * 性别 (0: 男声, 1: 女声)
     */
    private Boolean gender;

    /**
     * 是否展示 (0: 展示, 1: 不展示)
     */
    private Boolean isDisplay;

    /**
     * 逻辑删除 (0: 未删除, 1: 已删除)
     */
    private Boolean isDelete;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
