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
 * @Description: TODO
 * @ClassName DigitalVoiceCategoryMap
 * @Date: 2025/9/25 13:13
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_digital_voice_category_map")
public class DigitalVoiceCategoryMap {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 配音业务ID
     */
    private Long voiceId;

    /**
     * 分类业务ID
     */
    private Long voiceCategoryId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
