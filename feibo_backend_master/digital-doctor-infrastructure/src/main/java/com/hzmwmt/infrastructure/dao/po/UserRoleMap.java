package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserRoleMap
 * @Date: 2025/9/3 17:21
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_user_role_map")
public class UserRoleMap {
    /**
     * 主键
     */
    private Long id;

    /**
     * 用户业务ID
     */
    private Long userId;

    /**
     * 角色业务ID
     */
    private Long roleId;

    /**
     * 关联创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
