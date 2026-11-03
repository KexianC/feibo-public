package com.hzmwmt.domain.auth.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RoleEntity
 * @Date: 2025/9/5 11:29
 */
@Builder
@Data
public class RoleEntity {
    /**
     * 业务ID (雪花算法生成)
     */
    private Long roleId;

    /**
     * 角色英文名
     */
    private String name;

    /**
     * 角色中文描述
     */
    private String description;

    /**
     * 是否是管理员
     */
    private Boolean isAdmin;
}
