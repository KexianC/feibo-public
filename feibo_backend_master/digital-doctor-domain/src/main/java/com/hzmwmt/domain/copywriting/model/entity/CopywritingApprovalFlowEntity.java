package com.hzmwmt.domain.copywriting.model.entity;

import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: 审批流程实体
 * @ClassName CopywritingApprovalFlowEntity
 * @Date: 2025/8/20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CopywritingApprovalFlowEntity {


    /**
     * 审批流程业务ID
     */
    private Long copywritingApprovalFlowId;

    /**
     * 当前流程状态
     */
    private ApprovalStatusEnumVO currentStatus;


    /**
     * 创建时间
     */
    private Date createTime;


}