package com.hzmwmt.domain.entitlement;

import com.hzmwmt.domain.auth.service.IAuthService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/entitlements")
@PreAuthorize("isAuthenticated()")
public class EntitlementController {
    @Resource private IEntitlementService service;
    @Resource private IEntitlementAdminService adminService;
    @Resource private IAuthService authService;
    @GetMapping("/me") public UserResponse<Map<String, Object>> me() {
        return new UserResponse<>("0000", "成功", service.snapshot(authService.queryValidatedUser().getUserId()));
    }
    @GetMapping("/catalogue") public UserResponse<Map<String, Object>> catalogue() {
        return new UserResponse<>("0000", "成功", adminService.configuration(true));
    }
    public record UserResponse<T>(String code, String info, T data) {}
}
