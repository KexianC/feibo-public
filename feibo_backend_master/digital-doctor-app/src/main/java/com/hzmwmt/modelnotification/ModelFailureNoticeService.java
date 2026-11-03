package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.auth.service.IAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ModelFailureNoticeService {
    private final ModelFailureNoticeStore store;
    private final IAuthService auth;

    public ModelFailureNoticeService(ModelFailureNoticeStore store, IAuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    public record Page(List<ModelFailureNotice> records, long total, int current, int size, long unreadCount) {}

    public Page list(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        long adminId = currentAdmin();
        return new Page(store.list(adminId, pageSize, ((long) pageNum - 1) * pageSize),
                store.count(), pageNum, pageSize, store.unreadCount(adminId));
    }

    public long unreadCount() { return store.unreadCount(currentAdmin()); }

    public void markRead(String noticeId) {
        long parsed;
        try {
            if (noticeId == null || !noticeId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            parsed = Long.parseLong(noticeId);
        } catch (NumberFormatException invalidId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification ID");
        }
        long adminId = currentAdmin();
        if (!store.exists(parsed)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        store.markRead(adminId, parsed);
    }

    public void markAllRead() { store.markAllRead(currentAdmin()); }

    private long currentAdmin() { return auth.queryValidatedUser().getUserId(); }
}
