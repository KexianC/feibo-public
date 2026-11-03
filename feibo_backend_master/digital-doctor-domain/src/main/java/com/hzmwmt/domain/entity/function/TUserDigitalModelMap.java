package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数字模型映射实体类
 * 对应数据库表: t_user_digital_model_map
 *
 * @author system
 * @version v1.0
 */
@Data
@TableName("t_user_digital_model_map")
@Schema(name = "TUserDigitalModelMap", description = "用户数字模型映射实体类")
public class TUserDigitalModelMap {

    @Schema(description = "主键ID", example = "1")
    @TableId
    private Long id;

    @Schema(description = "用户ID", example = "700853889")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "数字模型ID", example = "165B8211B825830830C")
    @TableField("digital_model_id")
    private String digitalModelId;

    @Schema(description = "创建时间", example = "2025-10-27T15:54:00")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2025-10-27T15:54:00")
    @TableField("update_time")
    private LocalDateTime updateTime;
}