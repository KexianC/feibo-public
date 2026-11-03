package com.hzmwmt.domain.service.log.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hzmwmt.domain.entity.log.TLoginLog;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves display nicknames for audit log records without changing the
 * stable user identifiers persisted in the log tables.
 */
@Component
@RequiredArgsConstructor
public class LogNicknameEnricher {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[0-9]+$");

    private final AdminUserMapper adminUserMapper;

    public void enrichLoginLogs(List<TLoginLog> logs) {
        enrich(logs, TLoginLog::getLoginUser, TLoginLog::setNickName);
    }

    public void enrichOperationLogs(List<TOperationLogs> logs) {
        enrich(logs, TOperationLogs::getOperator, TOperationLogs::setNickName);
    }

    private <T> void enrich(List<T> logs,
                            Function<T, String> identifierGetter,
                            BiConsumer<T, String> nicknameSetter) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (T log : logs) {
            Long userId = parseUserId(identifierGetter.apply(log));
            if (userId != null) {
                userIds.add(userId);
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        List<TUser> users = adminUserMapper.selectList(
                new QueryWrapper<TUser>()
                        .select("user_id", "nick_name")
                        .in("user_id", userIds));

        Map<Long, String> nicknameByUserId = new HashMap<>();
        if (users != null) {
            for (TUser user : users) {
                if (user != null && user.getUserId() != null) {
                    nicknameByUserId.put(user.getUserId(), user.getNickName());
                }
            }
        }

        for (T log : logs) {
            Long userId = parseUserId(identifierGetter.apply(log));
            if (userId != null) {
                nicknameSetter.accept(log, nicknameByUserId.get(userId));
            }
        }
    }

    private Long parseUserId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (!USER_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
