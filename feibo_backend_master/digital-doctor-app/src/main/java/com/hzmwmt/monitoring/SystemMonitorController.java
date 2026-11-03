package com.hzmwmt.monitoring;

import com.hzmwmt.domain.common.Result;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
public class SystemMonitorController {
    private final SystemMonitorService service;

    public SystemMonitorController(SystemMonitorService service) { this.service = service; }

    @GetMapping("/monitor")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Result<SystemMonitorSnapshot>> monitor() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(service.snapshot()));
    }
}
