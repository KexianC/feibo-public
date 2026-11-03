package com.hzmwmt.config;

import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAuditDetailAggregate;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.infrastructure.adapter.repository.CopyWritingRepository;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.Copywriting;
import com.hzmwmt.infrastructure.dao.po.CopywritingAuditRecordView;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalFlow;
import com.hzmwmt.infrastructure.dao.po.User;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CopyWritingRepositoryAuditTest {

    private static final CopywritingDataScope OWNER_SCOPE =
            new CopywritingDataScope(202L, false, false, false);

    private final CopywritingApprovalHistoryMapper historyMapper = mock(CopywritingApprovalHistoryMapper.class);
    private final CopywritingMapper copywritingMapper = mock(CopywritingMapper.class);
    private final CopywritingApprovalFlowMapper flowMapper = mock(CopywritingApprovalFlowMapper.class);
    private final CopywritingCategoryMapper categoryMapper = mock(CopywritingCategoryMapper.class);
    private final CopywritingSourceMapper sourceMapper = mock(CopywritingSourceMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private CopyWritingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CopyWritingRepository();
        ReflectionTestUtils.setField(repository, "approvalHistoryMapper", historyMapper);
        ReflectionTestUtils.setField(repository, "copywritingMapper", copywritingMapper);
        ReflectionTestUtils.setField(repository, "approvalFlowMapper", flowMapper);
        ReflectionTestUtils.setField(repository, "copywritingCategoryMapper", categoryMapper);
        ReflectionTestUtils.setField(repository, "copywritingSourceMapper", sourceMapper);
        ReflectionTestUtils.setField(repository, "userMapper", userMapper);
    }

    @Test
    void unrelatedViewerIsRejectedBeforeFullContentIsLoaded() {
        when(historyMapper.selectVisibleCopywritingById(anyLong(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(null);

        AppException exception = assertThrows(AppException.class,
                () -> repository.queryCopywritingAuditDetail(101L, OWNER_SCOPE));

        assertEquals(ResponseCode.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(copywritingMapper);
    }

    @Test
    void missingFlowCategorySourceAndUsersReturnPartialDetailInsteadOfError() {
        when(historyMapper.selectVisibleCopywritingById(101L, 202L, false, false, false)).thenReturn(Copywriting.builder()
                .copywritingId(101L)
                .userId(202L)
                .title("完整标题")
                .content("完整文案")
                .status(999)
                .build());
        when(flowMapper.selectLatestByCopywritingId(101L)).thenReturn(null);
        when(userMapper.selectUserByUserId(202L)).thenReturn(null);
        CopywritingAuditDetailAggregate detail =
                repository.queryCopywritingAuditDetail(101L, OWNER_SCOPE);

        assertEquals("完整文案", detail.getCopywriting().getCopywritingEntity().getContent());
        assertNull(detail.getCopywriting().getCopywritingEntity().getStatus());
        assertNull(detail.getCopywriting().getCopywritingApprovalFlowEntity());
        assertNull(detail.getCopywriting().getCopywritingCategoryEntity());
        assertNull(detail.getCopywriting().getCopywritingSourceEntity());
        assertNull(detail.getCopywriting().getSubmitterUserEntity());
        assertTrue(detail.getAuditHistory().isEmpty());
    }

    @Test
    void legacyHistoryWithoutBusinessIdFallsBackToDatabaseId() {
        CopywritingAuditRecordView legacy = new CopywritingAuditRecordView();
        legacy.setHistoryDatabaseId(42L);
        legacy.setCopywritingApprovalHistoryId(null);
        legacy.setFromStatus(null);
        legacy.setToStatus(999);
        legacy.setOperationStatus(null);
        when(historyMapper.countVisibleAuditRecords(anyLong(), anyBoolean(), anyBoolean(), anyBoolean(),
                isNull(), isNull(), isNull())).thenReturn(1L);
        when(historyMapper.selectVisibleAuditRecords(anyLong(), anyBoolean(), anyBoolean(), anyBoolean(),
                isNull(), isNull(), isNull(), eq(0L), eq(10))).thenReturn(List.of(legacy));

        var page = repository.queryVisibleAuditRecords(OWNER_SCOPE,
                1, 10, null, null, null);

        assertEquals(42L, page.getData().get(0).getCopywritingApprovalHistoryId());
        assertNull(page.getData().get(0).getApproved());
        assertNull(page.getData().get(0).getFromStatus());
        assertEquals(999, page.getData().get(0).getToStatus());
    }

    @Test
    void invalidLegacyGenderFallsBackToUnknown() {
        when(historyMapper.selectVisibleCopywritingById(101L, 202L, false, false, false)).thenReturn(
                Copywriting.builder().copywritingId(101L).userId(202L).status(1).build());
        when(userMapper.selectUserByUserId(202L)).thenReturn(
                User.builder().userId(202L).nickName("旧账号").gender(999).build());
        when(flowMapper.selectLatestByCopywritingId(101L)).thenReturn(null);
        var detail = repository.queryCopywritingAuditDetail(101L, OWNER_SCOPE);

        assertEquals(GenderEnumVO.UNKNOWN,
                detail.getCopywriting().getSubmitterUserEntity().getGenderEnumVO());
    }

    @Test
    void detailUsesLatestCurrentFlowButReturnsAllReviewRounds() {
        when(historyMapper.selectVisibleCopywritingById(101L, 202L, false, false, false)).thenReturn(
                Copywriting.builder().copywritingId(101L).userId(202L).status(1).build());
        when(flowMapper.selectLatestByCopywritingId(101L)).thenReturn(
                CopywritingApprovalFlow.builder()
                        .copywritingApprovalFlowId(501L)
                        .copywritingId(101L)
                        .currentStatus(1)
                        .build());
        CopywritingAuditRecordView currentRound = new CopywritingAuditRecordView();
        currentRound.setHistoryDatabaseId(1L);
        currentRound.setCopywritingApprovalFlowId(501L);
        CopywritingAuditRecordView earlierRound = new CopywritingAuditRecordView();
        earlierRound.setHistoryDatabaseId(2L);
        earlierRound.setCopywritingApprovalFlowId(500L);
        when(historyMapper.selectVisibleAuditRecordsByCopywritingId(101L, 202L, false, false, false))
                .thenReturn(List.of(earlierRound, currentRound));

        var detail = repository.queryCopywritingAuditDetail(101L, OWNER_SCOPE);

        assertEquals(501L, detail.getCopywriting().getCopywritingApprovalFlowEntity()
                .getCopywritingApprovalFlowId());
        assertEquals(List.of(500L, 501L), detail.getAuditHistory().stream()
                .map(record -> record.getCopywritingApprovalFlowId())
                .toList());
        verify(historyMapper).selectVisibleAuditRecordsByCopywritingId(101L, 202L, false, false, false);
    }
}
