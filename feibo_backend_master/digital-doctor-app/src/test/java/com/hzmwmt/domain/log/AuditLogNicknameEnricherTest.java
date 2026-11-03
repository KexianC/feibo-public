package com.hzmwmt.domain.log;

import com.baomidou.mybatisplus.annotation.TableField;
import com.hzmwmt.domain.entity.log.TLoginLog;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.log.support.LogNicknameEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogNicknameEnricherTest {

    @Mock
    private AdminUserMapper adminUserMapper;

    private LogNicknameEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new LogNicknameEnricher(adminUserMapper);
    }

    @Test
    void loginLogsResolveKnownUserIdsInOneBatchAndIgnoreLegacyIdentifiers() {
        TLoginLog first = loginLog("1001");
        TLoginLog duplicate = loginLog("1001");
        TLoginLog maskedPhone = loginLog("131****4211");
        TLoginLog anonymous = loginLog("anonymous");
        TLoginLog overflow = loginLog("999999999999999999999999999999");

        TUser user = user(1001L, "测试昵称");
        when(adminUserMapper.selectList(any())).thenReturn(List.of(user));

        enricher.enrichLoginLogs(List.of(first, duplicate, maskedPhone, anonymous, overflow));

        assertEquals("测试昵称", first.getNickName());
        assertEquals("测试昵称", duplicate.getNickName());
        assertNull(maskedPhone.getNickName());
        assertNull(anonymous.getNickName());
        assertNull(overflow.getNickName());
        assertEquals("1001", first.getLoginUser());
        assertEquals("131****4211", maskedPhone.getLoginUser());
        verify(adminUserMapper, times(1)).selectList(any());
    }

    @Test
    void operationLogsKeepIdentifiersAndLeaveMissingOrEmptyNicknamesBlank() {
        TOperationLogs known = operationLog("2002");
        TOperationLogs missing = operationLog("3003");
        TOperationLogs emptyNickname = operationLog("4004");

        when(adminUserMapper.selectList(any()))
                .thenReturn(List.of(user(2002L, "管理员甲"), user(4004L, "")));

        enricher.enrichOperationLogs(List.of(known, missing, emptyNickname));

        assertEquals("管理员甲", known.getNickName());
        assertNull(missing.getNickName());
        assertEquals("", emptyNickname.getNickName());
        assertEquals("2002", known.getOperator());
        assertEquals("3003", missing.getOperator());
    }

    @Test
    void invalidOnlyPageDoesNotQueryUsers() {
        enricher.enrichLoginLogs(List.of(loginLog("unknown"), loginLog("131****4211")));
        enricher.enrichOperationLogs(List.of(operationLog("anonymous")));

        verifyNoInteractions(adminUserMapper);
    }

    @Test
    void nicknameFieldsAreReadOnlyDatabaseProjections() throws Exception {
        assertTransientNickname(TLoginLog.class);
        assertTransientNickname(TOperationLogs.class);
    }

    private void assertTransientNickname(Class<?> entityType) throws Exception {
        Field field = entityType.getDeclaredField("nickName");
        TableField tableField = field.getAnnotation(TableField.class);
        assertFalse(tableField.exist());
    }

    private TLoginLog loginLog(String loginUser) {
        TLoginLog log = new TLoginLog();
        log.setLoginUser(loginUser);
        return log;
    }

    private TOperationLogs operationLog(String operator) {
        TOperationLogs log = new TOperationLogs();
        log.setOperator(operator);
        return log;
    }

    private TUser user(Long userId, String nickName) {
        TUser user = new TUser();
        user.setUserId(userId);
        user.setNickName(nickName);
        return user;
    }
}
