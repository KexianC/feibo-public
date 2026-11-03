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
 * @ClassName CopywritingApprovalHistory
 * @Date: 2025/9/3 17:24
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_copywriting_approval_history")
public class CopywritingApprovalHistory {

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 业务ID (雪花算法生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long copywritingApprovalHistoryId;

    /**
     * 审批流程ID
     */
    private Long copywritingApprovalFlowId;

    /**
     * 操作人用户业务ID
     */
    private Long operatorId;

    /**
     * 操作类型 ( APPROVE, REJECT)
     */
    private Boolean operationStatus;

    /**
     * 修改意见或备注
     */
    private String comment;

    /**
     * 操作前状态
     */
    private Integer fromStatus;

    /**
     * 操作后状态
     */
    private Integer toStatus;

    /**
     * 操作时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
