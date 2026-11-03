package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingApprovalFlow
 * @Date: 2025/9/3 17:23
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_copywriting_approval_flow")
public class CopywritingApprovalFlow {

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID (雪花算法生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long copywritingApprovalFlowId;

    /**
     * 文案业务ID
     */
    private Long copywritingId;

    /**
     * 当前流程状态
     */
    private Integer currentStatus;

    /**
     * 当前处理人业务ID (若为NULL, 表示在公共任务池)
     */
    private Long currentProcessorId;

    /** Server-derived authorization inputs used only by explicit workflow SQL. */
    @TableField(exist = false)
    private Boolean globalAccess;

    @TableField(exist = false)
    private Long scopeActorId;


    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
