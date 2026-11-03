package com.hzmwmt.domain.copywriting.adapter.repository;

import com.hzmwmt.domain.copywriting.model.aggregate.*;
import com.hzmwmt.domain.copywriting.model.entity.ClaimTaskEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingCategoryEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingSourceEntity;
import com.hzmwmt.domain.copywriting.model.entity.DelegateCommandEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.types.common.OffsetPageResponseData;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ICopyWritingRepository
 * @Date: 2025/9/7 13:12
 */
public interface ICopyWritingRepository {

    Long submit(CreateCopyWritingAggregate createCopyWritingAggregate);

    void approvalByDoctor(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void approvalByExpert(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void approvalByPlatform(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void approvalByAI(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    void reject(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate);

    CopyWritingApprovalFlowAggregate queryApprovalFlow(Long approvalFlowId);

    void delegateToAssistant(DelegateCommandEntity delegateCommandEntity);

    void claimApprovalTaskByExpert(ClaimTaskEntity claimTaskEntity);

    void claimApprovalTaskByPlatForm(ClaimTaskEntity claimTaskEntity);

    OffsetPageResponseData<CopywritingAggregate> queryCopywritingByCopywritingStatus(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            CopywritingStatusEnumVO copywritingStatusEnumVO);

    OffsetPageResponseData<ApprovalCopywritingAggregate> queryCopywritingByApprovalStatus(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            ApprovalStatusEnumVO approvalStatusEnumVO);

    CopywritingAuditDetailAggregate queryCopywritingAuditDetail(
            Long copywritingId,
            CopywritingDataScope dataScope);

    OffsetPageResponseData<com.hzmwmt.domain.copywriting.model.entity.CopywritingAuditRecordEntity> queryVisibleAuditRecords(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            Long copywritingId,
            Integer auditorStage,
            Boolean approved);

    List<CopywritingCategoryEntity> queryAllValidCopywritingCategory();

    List<CopywritingSourceEntity> queryAllValidCopywritingSource();

    void createCopywritingCategory(String name);

    void createCopywritingSource(String name);

}
