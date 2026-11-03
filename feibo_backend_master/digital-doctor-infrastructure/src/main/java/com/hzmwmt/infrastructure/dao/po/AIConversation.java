package com.hzmwmt.infrastructure.dao.po;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIConversation
 * @Date: 2025/8/11 11:23
 */

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * AI 会话表实体类 (PO)
 *
 * @author huihui
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_conversation")
public class AIConversation {

    /**
     * 主键ID，数据库自增
     */
    private Long id;

    /**
     * 业务会话ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiConversationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;


    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最后更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private Long consumption;

    private BigDecimal billing;
}