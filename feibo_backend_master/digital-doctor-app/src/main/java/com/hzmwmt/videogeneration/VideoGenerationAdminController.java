package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/video-generation")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class VideoGenerationAdminController {
    private final VideoGenerationService service;
    public VideoGenerationAdminController(VideoGenerationService service) { this.service = service; }
    @GetMapping("/providers") public Result<List<VideoGenerationService.Provider>> providers() { return Result.success(service.providers(true)); }
    @GetMapping("/tasks") public Result<List<VideoGenerationService.Task>> tasks() { return Result.success(service.list(null)); }
    @PutMapping("/providers/{provider}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType="修改", description="配置 AI 视频供应商", recordParams=false, recordResult=false, critical=true)
    public Result<Void> save(@PathVariable String provider, @Valid @RequestBody VideoGenerationService.Settings body) {
        service.save(provider, body); return Result.success();
    }
}
