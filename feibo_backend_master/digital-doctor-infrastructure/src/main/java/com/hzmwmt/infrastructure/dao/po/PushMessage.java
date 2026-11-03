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
 * @ClassName PushMessage
 * @Date: 2025/9/2 16:32
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_push_message")
public class PushMessage {

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 雪花算法生成的推送消息ID，作为业务唯一标识
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long pushMessageId;

    /**
     * 接收方用户ID
     */
    private Long userId;

    /**
     * 推送标题
     */
    private String title;

    /**
     * 推送内容
     */
    private String content;

    /**
     * 消息状态 (false: 未读, true: 已读)
     */
    private Boolean status;

    /**
     * 消息创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 消息发送时间
     */
    private Date sendTime;

}
