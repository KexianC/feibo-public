package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.common.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/model-failure-notices")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ModelFailureNoticeController {
    private final ModelFailureNoticeService service;

    public ModelFailureNoticeController(ModelFailureNoticeService service) { this.service = service; }

    @GetMapping
    public Result<ModelFailureNoticeService.Page> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.list(pageNum, pageSize));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() { return Result.success(service.unreadCount()); }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable String id) { service.markRead(id); return Result.success(); }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() { service.markAllRead(); return Result.success(); }
}
