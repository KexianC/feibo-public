package com.hzmwmt.domain.copywriting.service.manager;

import com.hzmwmt.domain.copywriting.adapter.repository.ICopyWritingRepository;
import com.hzmwmt.domain.copywriting.model.aggregate.ApprovalCopywritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAuditDetailAggregate;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingAuditRecordEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingCategoryEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingSourceEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.domain.copywriting.service.ICopywritingManagerService;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingManagerService
 * @Date: 2025/9/7 13:11
 */
@Service
public class CopywritingManagerService implements ICopywritingManagerService {
    @Resource
    private ICopyWritingRepository copyWritingRepository;

    @Override
    public OffsetPageResponseData<CopywritingAggregate> queryCopywritingByCopywritingStatus(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            CopywritingStatusEnumVO copywritingStatusEnumVO) {
        return copyWritingRepository.queryCopywritingByCopywritingStatus(dataScope, page, size, copywritingStatusEnumVO);
    }

    @Override
    public OffsetPageResponseData<ApprovalCopywritingAggregate> queryCopywritingByApprovalStatus(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            ApprovalStatusEnumVO approvalStatusEnumVO) {
        return copyWritingRepository.queryCopywritingByApprovalStatus(
                dataScope,
                page,
                size,
                approvalStatusEnumVO);
    }

    @Override
    public CopywritingAuditDetailAggregate queryCopywritingAuditDetail(
            Long copywritingId,
            CopywritingDataScope dataScope) {
        return copyWritingRepository.queryCopywritingAuditDetail(
                copywritingId,
                dataScope);
    }

    @Override
    public OffsetPageResponseData<CopywritingAuditRecordEntity> queryVisibleAuditRecords(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            Long copywritingId,
            Integer auditorStage,
            Boolean approved) {
        return copyWritingRepository.queryVisibleAuditRecords(
                dataScope,
                page,
                size,
                copywritingId,
                auditorStage,
                approved);
    }


    @Override
    public List<CopywritingCategoryEntity> queryAllValidCopywritingCategory() {
        return copyWritingRepository.queryAllValidCopywritingCategory();
    }

    @Override
    public List<CopywritingSourceEntity> queryAllValidCopywritingSource() {
        return copyWritingRepository.queryAllValidCopywritingSource();
    }

    @Override
    @PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "新增", description = "创建文案分类", recordResult = false, critical = true)
    public void createCopywritingCategory(String name) {
        copyWritingRepository.createCopywritingCategory(name.trim());
    }

    @Override
    @PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "新增", description = "创建文案来源", recordResult = false, critical = true)
    public void createCopywritingSource(String name) {
        copyWritingRepository.createCopywritingSource(name.trim());
    }

}
