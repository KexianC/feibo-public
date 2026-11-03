package com.hzmwmt.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthUserResponseDTO
 * @Date: 2025/9/4 15:17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor // 确保有这个注解
public class AuthUserResponseDTO {
    private Long userId;
    private String nickName;
    private String avatar;
    private String phoneNumber;
    private String gender;
    private String accessToken;
    private String refreshToken;
    private String tokenHead;
    private Integer loginType;        // 确保字段类型是 Integer
    private String loginTypeDesc;
    private Boolean hasAdminPermission;
    private List<RoleDTO> roles;
    /** Capability codes resolved from all current role assignments. */
    private List<String> permissions;


    /**
     * 内嵌的角色信息DTO，用于在API层表示角色实体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDTO {
        private Long roleId;
        private String name;
        private String description;
        private Boolean isAdmin;
    }
}
