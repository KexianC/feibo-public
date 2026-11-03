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
 * @ClassName ExpertAssistantMap
 * @Date: 2025/9/6 16:26
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_expert_assistant_map")
public class ExpertAssistantMap {
    /**
     * 主键
     */
    private Long id;
    /**
     * 专家ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long expertUserId;
    /**
     * 助手ID
     */
    private Long assistantUserId;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
