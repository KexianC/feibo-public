package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_copywriting_expert_assignment")
public class CopywritingExpertAssignment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("account_user_id")
    private Long accountUserId;

    @TableField("expert_user_id")
    private Long expertUserId;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
