package com.hzmwmt.security;

import com.hzmwmt.domain.entity.function.TUserDigitalModelMap;
import com.hzmwmt.domain.mapper.function.AdminDigitalModelMapper;
import com.hzmwmt.domain.mapper.function.TUserDigitalModelMapMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.function.impl.DigitalModelServiceImpl;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalModelBindingSecurityTest {

    private AdminDigitalModelMapper modelMapper;
    private TUserDigitalModelMapMapper bindingMapper;
    private AdminUserMapper userMapper;
    private DigitalModelServiceImpl service;

    @BeforeEach
    void setUp() {
        modelMapper = mock(AdminDigitalModelMapper.class);
        bindingMapper = mock(TUserDigitalModelMapMapper.class);
        userMapper = mock(AdminUserMapper.class);
        service = new DigitalModelServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", modelMapper);
        ReflectionTestUtils.setField(service, "userDigitalModelMapMapper", bindingMapper);
        ReflectionTestUtils.setField(service, "adminUserMapper", userMapper);
        when(userMapper.selectCount(any())).thenReturn(1L);
    }

    @Test
    void invalidModelIsRejectedBeforeExistingBindingsAreDeleted() {
        when(modelMapper.selectCount(any())).thenReturn(1L);

        assertThrows(AppException.class,
                () -> service.updateUserModelBindings(100L, List.of(1L, 2L)));

        verify(bindingMapper, never()).delete(any());
        verify(bindingMapper, never()).insert(any(TUserDigitalModelMap.class));
    }

    @Test
    void validModelsReplaceBindingsAndDuplicateInputIsCollapsed() {
        when(modelMapper.selectCount(any())).thenReturn(2L);
        when(bindingMapper.insert(any(TUserDigitalModelMap.class))).thenReturn(1);

        assertTrue(service.updateUserModelBindings(100L, List.of(1L, 2L, 2L)));

        verify(bindingMapper).delete(any());
        verify(bindingMapper, org.mockito.Mockito.times(2))
                .insert(any(TUserDigitalModelMap.class));
    }
}
