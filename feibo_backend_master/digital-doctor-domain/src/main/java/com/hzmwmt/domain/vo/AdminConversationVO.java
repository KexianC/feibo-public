package com.hzmwmt.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员端对话信息的展示VO（用于列表展示对话的基础信息）
 */
@Data
@Schema(description = "管理员端对话列表展示的视图对象，包含对话ID、用户信息、问题标题、使用模型等基础信息")
public class AdminConversationVO {

    @Schema(
            description = "对话ID（主键，唯一标识一个对话）",
            example = "10001"
    )
    // 对话id
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long id;

    @Schema(
            description = "发起对话的用户名",
            example = "张三"
    )
    // 用户
    private String username;

    @Schema(
            description = "对话中的问题标题（取自会话表的描述字段）",
            example = "如何配置Swagger注解？"
    )
    // 问题 就是会话表中的描述
    private String title;

    @Schema(
            description = "对话使用的AI模型名称",
            example = "GPT-3.5"
    )
    // 模型
    private String model;

    @Schema(
            description = "对话创建时间（取自会话表）",
            example = "2023-10-15T14:30:00"
    )
    // 创建时间 从会话表中取
    private LocalDateTime createTime;

    @Schema(
            description = "该对话消耗的token数量",
            example = "150"
    )
    // token消耗
    private Integer tokenNumber;

    private Long measuredTokens;
    private java.math.BigDecimal estimatedCost;
    private int meteredReplies;
    private int pricedReplies;
    private int totalReplies;
}
