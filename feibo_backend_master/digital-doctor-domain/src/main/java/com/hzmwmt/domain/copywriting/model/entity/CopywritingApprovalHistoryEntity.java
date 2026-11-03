package com.hzmwmt.domain.copywriting.model.entity;

import com.hzmwmt.domain.copywriting.model.valobj.ApprovalOperationStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingApprovalHistoryEntity
 * @Date: 2025/9/8 17:13
 */
@Builder
@AllArgsConstructor
@Data
public class CopywritingApprovalHistoryEntity {
    /**
     * 业务ID (雪花算法生成)
     */

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
    private ApprovalOperationStatusEnumVO approvalOperationStatusEnumVO;

    /**
     * 修改意见或备注
     */
    private String comment;

    /**
     * 操作前状态
     */
    private ApprovalStatusEnumVO fromStatus;

    /**
     * 操作后状态
     */
    private ApprovalStatusEnumVO toStatus;

    /**
     * 操作时间
     */
    private Date createTime;
}
