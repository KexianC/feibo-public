package com.hzmwmt.domain.copywriting.service;

import com.hzmwmt.domain.copywriting.model.aggregate.CreateCopyWritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.ClaimTaskEntity;
import com.hzmwmt.domain.copywriting.model.entity.DelegateCommandEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ICopyWritingApprovalService
 * @Date: 2025/9/6 17:36
 */
public interface ICopyWritingApprovalService {
    Long submit(CreateCopyWritingAggregate createCopyWritingAggregate);

    void approvalByAI(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void approvalByDoctor(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void approvalByExpert(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void approvalByPlatform(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void delegateToAssistant(DelegateCommandEntity delegateCommandEntity);

    void claimApprovalTaskByExpert(ClaimTaskEntity claimTaskEntity);

    void claimApprovalTaskByPlatform(ClaimTaskEntity claimTaskEntity);

    void reject(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);
}
