package com.hzmwmt.domain.copywriting.service;

import com.hzmwmt.domain.copywriting.model.aggregate.ApprovalCopywritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAuditDetailAggregate;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingAuditRecordEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingCategoryEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingSourceEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.types.common.OffsetPageResponseData;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ICopywritingManagerService
 * @Date: 2025/9/6 17:36
 */
public interface ICopywritingManagerService {
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

    OffsetPageResponseData<CopywritingAuditRecordEntity> queryVisibleAuditRecords(
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
