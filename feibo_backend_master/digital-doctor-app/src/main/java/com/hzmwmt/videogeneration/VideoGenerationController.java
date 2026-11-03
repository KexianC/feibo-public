package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.auth.service.IAuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/video-generation")
@PreAuthorize("isAuthenticated()")
public class VideoGenerationController {
    private final VideoGenerationService service;
    private final IAuthService auth;
    public VideoGenerationController(VideoGenerationService service, IAuthService auth) { this.service = service; this.auth = auth; }
    private String owner() { return String.valueOf(auth.queryValidatedUser().getUserId()); }
    @GetMapping("/providers") public Result<List<VideoGenerationService.Provider>> providers() { return Result.success(service.providers(false)); }
    @GetMapping("/tasks") public Result<List<VideoGenerationService.Task>> tasks() { return Result.success(service.list(owner())); }
    @PostMapping("/tasks") public Result<VideoGenerationService.Task> create(@Valid @RequestBody VideoGenerationService.Create body) { return Result.success(service.create(owner(), body)); }
    @GetMapping("/tasks/{id}") public Result<VideoGenerationService.Task> refresh(@PathVariable String id) { return Result.success(service.refresh(owner(), id)); }
}
