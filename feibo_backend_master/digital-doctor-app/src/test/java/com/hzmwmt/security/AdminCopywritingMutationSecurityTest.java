package com.hzmwmt.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalFlow;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalFlowMapper;
import com.hzmwmt.domain.mapper.function.AdminCopywritingMapper;
import com.hzmwmt.domain.service.function.impl.AdminCopywritingServiceImpl;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminCopywritingMutationSecurityTest {

    private final AdminCopywritingMapper copywritingMapper = mock(AdminCopywritingMapper.class);
    private final AdminCopywritingApprovalFlowMapper flowMapper = mock(AdminCopywritingApprovalFlowMapper.class);
    private AdminCopywritingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminCopywritingServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", copywritingMapper);
        ReflectionTestUtils.setField(service, "adminCopywritingApprovalFlowMapper", flowMapper);
    }

    @Test
    void createIgnoresClientControlledIdentifiersStatusAndTimestamps() {
        when(copywritingMapper.insert(any(TCopywriting.class))).thenReturn(1);
        TCopywriting request = validCopywriting();
        request.setId(999L);
        request.setCopywritingId(888L);
        request.setStatus(4);
        request.setCreateTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        request.setUpdateTime(LocalDateTime.of(2020, 1, 2, 0, 0));

        assertTrue(service.createCopywritingSafely(request));

        ArgumentCaptor<TCopywriting> inserted = ArgumentCaptor.forClass(TCopywriting.class);
        verify(copywritingMapper).insert(inserted.capture());
        assertNull(inserted.getValue().getId());
        assertNotNull(inserted.getValue().getCopywritingId());
        assertNotEquals(888L, inserted.getValue().getCopywritingId());
        assertEquals(0, inserted.getValue().getStatus());
        assertNull(inserted.getValue().getCreateTime());
        assertNull(inserted.getValue().getUpdateTime());
    }

    @Test
    void updateCannotRewriteOwnerWorkflowIdentityStatusOrTimestamps() {
        TCopywriting existing = validCopywriting();
        existing.setId(10L);
        existing.setCopywritingId(1001L);
        when(copywritingMapper.selectById(10L)).thenReturn(existing);
        when(flowMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(copywritingMapper.updateById(any(TCopywriting.class))).thenReturn(1);

        TCopywriting request = validCopywriting();
        request.setId(10L);
        request.setCopywritingId(9999L);
        request.setUserId(999L);
        request.setStatus(4);
        request.setCreateTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        request.setUpdateTime(LocalDateTime.of(2020, 1, 2, 0, 0));

        assertTrue(service.updateCopywritingSafely(request));

        ArgumentCaptor<TCopywriting> updated = ArgumentCaptor.forClass(TCopywriting.class);
        verify(copywritingMapper).updateById(updated.capture());
        assertNull(updated.getValue().getCopywritingId());
        assertNull(updated.getValue().getUserId());
        assertNull(updated.getValue().getStatus());
        assertNull(updated.getValue().getCreateTime());
        assertNull(updated.getValue().getUpdateTime());
    }

    @Test
    void approvedOrPendingCopywritingCannotBeEditedOrDeleted() {
        TCopywriting existing = validCopywriting();
        existing.setId(10L);
        existing.setCopywritingId(1001L);
        when(copywritingMapper.selectById(10L)).thenReturn(existing);
        when(flowMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        TCopywriting update = validCopywriting();
        update.setId(10L);
        assertThrows(AppException.class, () -> service.updateCopywritingSafely(update));
        assertThrows(AppException.class, () -> service.removeCopywritingSafely(10L));

        verify(copywritingMapper, never()).updateById(any(TCopywriting.class));
        verify(copywritingMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    private TCopywriting validCopywriting() {
        TCopywriting copywriting = new TCopywriting();
        copywriting.setUserId(101L);
        copywriting.setCopywritingCategoryId(201L);
        copywriting.setCopywritingSourceId(301L);
        copywriting.setTitle("title");
        copywriting.setSummary("summary");
        copywriting.setContent("content");
        return copywriting;
    }
}
