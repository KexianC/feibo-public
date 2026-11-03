package com.hzmwmt.domain.vo.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(name = "AdminUserRoleMapVO", description = "用户角色映射视图对象（前端展示用）")
public class AdminUserRoleMapVO {
    private Integer id;
    private Long userId;
    @Schema(description = "用户昵称（对应t_user表的nick_name）")
    private String username;  // 此处实际展示的是用户表的nick_name
    private Long roleId;
    private String roleName;
    private LocalDateTime createTime;
}