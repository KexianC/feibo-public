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
 * @ClassName DigitalVoiceCategory
 * @Date: 2025/9/25 11:45
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_digital_voice_category")
public class DigitalVoiceCategory {
    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 分类业务ID (雪花算法)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalVoiceCategoryId;

    /**
     * 名称
     */
    private String name;

    /**
     * 类型 (0:年龄, 1:领域, 2:风格, 3:语言)
     */
    private Integer type;

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

