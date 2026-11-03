package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * AI 消息记录表实体类 (PO)
 *
 * @author 灰灰
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_ai_message")
public class AIMessage {

    /**
     * 主键ID，数据库自增
     */
    private Long id;

    /**
     * 业务消息ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiMessageId;

    /**
     * 所属会话的业务ID
     */
    private Long aiConversationId;

    /**
     * 约定: false 代表 user (用户), true 代表 assistant (AI助手)
     */
    private Boolean role;

    /**
     * 模型ID
     */
    private Long aiModelConfigId;

    /**
     * 消息的具体内容
     */
    private String content;


    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    private Long token;

    private String usageJson;
}
