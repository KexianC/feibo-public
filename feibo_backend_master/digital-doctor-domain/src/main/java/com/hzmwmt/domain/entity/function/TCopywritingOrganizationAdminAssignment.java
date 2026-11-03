package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_copywriting_organization_admin_assignment")
public class TCopywritingOrganizationAdminAssignment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("account_user_id")
    private Long accountUserId;

    @TableField("admin_user_id")
    private Long adminUserId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
