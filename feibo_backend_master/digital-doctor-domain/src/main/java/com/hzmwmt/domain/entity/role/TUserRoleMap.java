package com.hzmwmt.domain.entity.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_role_map")
@Schema(name = "TUserRoleMap", description = "用户角色映射实体类")
public class TUserRoleMap {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer id;

    @TableField("user_id")
    @Schema(description = "用户ID")
    private Long userId;

    @TableField("role_id")
    @Schema(description = "角色ID")
    private Long roleId;

    @TableField("create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
