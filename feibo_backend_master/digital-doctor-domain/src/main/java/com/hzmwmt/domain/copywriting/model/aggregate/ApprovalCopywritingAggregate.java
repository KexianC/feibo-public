package com.hzmwmt.domain.copywriting.model.aggregate;

import com.hzmwmt.domain.copywriting.model.entity.*;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalCopywritingAggregate
 * @Date: 2025/9/10 11:45
 */
@Builder
@Data
public class ApprovalCopywritingAggregate {
    /**
     * 文案实体
     */
    private CopywritingEntity copywritingEntity;

    /**
     * 文案分类实体
     */
    private CopywritingCategoryEntity copywritingCategoryEntity;

    /**
     * 文案来源实体
     */
    private CopywritingSourceEntity copywritingSourceEntity;

    /**
     * 审批流实体
     */
    private CopywritingApprovalFlowEntity copywritingApprovalFlowEntity;

    /**
     * 审批流历史实体
     */
    private CopywritingApprovalHistoryEntity copywritingApprovalHistoryEntity;


    /**
     * 审批人实体
     */
    private ApprovalUserEntity approvalUserEntity;

    /** Copywriting submitter. */
    private ApprovalUserEntity submitterUserEntity;

    /** Viewer is currently assigned as an eligible expert for this author. */
    private Boolean expertReviewer;

    /** Viewer is currently assigned as an eligible organization final reviewer. */
    private Boolean organizationReviewer;
}
