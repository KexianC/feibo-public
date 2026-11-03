package com.hzmwmt.domain.entitlement;

import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/admin/entitlements")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Transactional(rollbackFor=Exception.class)
public class AdminEntitlementController {
    @Resource private IEntitlementService service;
    @Resource private IEntitlementAdminService adminService;
    @Resource private IAuthService authService;
    @GetMapping("/config") public Result<Map<String, Object>> config() { return Result.success(adminService.configuration(false)); }
    @PutMapping("/free-plan")
    @OperationLog(operationType="修改", description="修改免费会员权益", critical=true)
    public Result<Void> free(@Valid @RequestBody FreeEntitlementPolicy request) {
        adminService.updateFreePlan(request); return Result.success();
    }
    @PostMapping("/plans")
    @OperationLog(operationType="新增",description="新增会员类型及权益",critical=true)
    public Result<Map<String,Object>> newPlan(@Valid @RequestBody EntitlementProductRequest request) {
        return Result.success(adminService.saveProduct(false, null, request));
    }
    @PutMapping("/plans/{productId}")
    @OperationLog(operationType="修改",description="修改会员类型及权益",critical=true)
    public Result<Map<String,Object>> plan(@PathVariable @Positive Long productId, @Valid @RequestBody EntitlementProductRequest request) {
        return Result.success(adminService.saveProduct(false, productId, request));
    }
    @PostMapping("/packs")
    @OperationLog(operationType="新增",description="新增权益充值包",critical=true)
    public Result<Map<String,Object>> newPack(@Valid @RequestBody EntitlementProductRequest request) {
        return Result.success(adminService.saveProduct(true, null, request));
    }
    @PutMapping("/packs/{productId}")
    @OperationLog(operationType="修改",description="修改权益充值包",critical=true)
    public Result<Map<String,Object>> pack(@PathVariable @Positive Long productId, @Valid @RequestBody EntitlementProductRequest request) {
        return Result.success(adminService.saveProduct(true, productId, request));
    }
    @PutMapping("/models/{modelId}")
    @OperationLog(operationType="修改",description="修改模型普通或高级分类",critical=true)
    public Result<Void> model(@PathVariable @Positive Long modelId, @Valid @RequestBody ModelTier request) {
        adminService.setModelPremium(modelId, request.premium()); return Result.success();
    }
    @GetMapping("/accounts/{userId}") public Result<Map<String,Object>> account(@PathVariable @Positive Long userId) { return Result.success(service.snapshot(userId)); }
    @PostMapping("/accounts/{userId}/grants")
    @OperationLog(operationType="新增", description="发放账号会员权益", recordParams=false, recordResult=false, critical=true)
    public Result<Map<String,Object>> grant(@PathVariable @Positive Long userId, @Valid @RequestBody EntitlementGrantRequest request) {
        service.grant(userId, request, authService.queryValidatedUser().getUserId()); return Result.success(service.snapshot(userId));
    }
    public record ModelTier(@NotNull Boolean premium) {}
}
