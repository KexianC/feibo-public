package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 配音库与风格分类的映射关系表实体类
 * @ClassName VoiceLibraryStyleMap
 * @Date: 2025/8/14 16:13
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_digital_voice_style_map")
public class DigitalVoiceStyleMap {
    /**
     * 配音ID
     */
    private Long voiceId;

    /**
     * 风格分类ID
     */
    private Long voiceStyleCategoryId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}