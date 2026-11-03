package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class VideoQuotaReconciliationController {
    private final VideoQuotaReconciliationService service;
    private final IAuthService auth;
    public VideoQuotaReconciliationController(VideoQuotaReconciliationService service,IAuthService auth) { this.service=service; this.auth=auth; }
    @GetMapping("/api/admin/user/digital-works/pending-quota")
    public Result<List<Map<String,Object>>> pending() { return Result.success(service.digitalPending()); }
    @PostMapping("/api/v1/admin/video-generation/tasks/{id}/resolve-failed")
    public Result<Void> resolveAi(@PathVariable String id,@Valid @RequestBody VideoQuotaReconciliationService.ResolveFailure request) {
        service.resolveAi(id,request,auth.queryValidatedUser().getUserId()); return Result.success();
    }
    @PostMapping("/api/admin/user/digital-works/{id}/resolve-failed")
    public Result<Void> resolveDigital(@PathVariable Long id,@Valid @RequestBody VideoQuotaReconciliationService.ResolveFailure request) {
        service.resolveDigital(id,request,auth.queryValidatedUser().getUserId()); return Result.success();
    }
}
