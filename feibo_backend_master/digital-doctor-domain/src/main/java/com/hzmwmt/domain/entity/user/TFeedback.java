package com.hzmwmt.domain.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈实体类
 * 对应数据库表：t_feedback（需根据实际表名修改）
 */
@Data
@TableName("t_feedback") // 替换为实际表名
@Schema(
        description = "反馈实体类，对应数据库表t_feedback，存储用户反馈的基本信息（如反馈类型、标题、关联用户及创建时间等）"
)
public class TFeedback {

    /**
     * 主键 ID（自增，若为非自增策略，需调整 @TableId 的 type）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条反馈记录）",
            example = "1001"
    )
    private Long id;

    /**
     * 反馈关联 ID（关联反馈主表等场景）
     */
    @TableField("feedback_id")
    @Schema(
            description = "反馈关联ID（用于关联反馈主表或其他相关表的业务标识）",
            example = "20001"
    )
    private Long feedbackId;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    @Schema(
            description = "用户ID（关联提交反馈的用户）",
            example = "10086"
    )
    private Long userId;

    /**
     * 反馈类型（数值类型，业务中可通过枚举定义具体类型含义）
     */
    @TableField("type")
    @Schema(
            description = "反馈类型（数值枚举，如1-功能建议、2-bug反馈、3-体验优化等，具体含义参考业务枚举）",
            example = "2"
    )
    private Long type;

    /**
     * 反馈标题
     */
    @TableField("title")
    @Schema(
            description = "反馈标题（简要描述反馈的核心内容）",
            example = "登录页面验证码加载失败"
    )
    private String title;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(
            description = "反馈创建时间（数据库自动生成，标识用户提交反馈的时刻）",
            example = "2024-09-20T09:45:00"
    )
    private LocalDateTime createTime;
    @TableField("status")
    private Integer status;
    @TableField("priority")
    private Integer priority;
    @TableField("reply")
    private String reply;
    @TableField("version")
    private Long version;
    @TableField("handler_user_id")
    private Long handlerUserId;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
