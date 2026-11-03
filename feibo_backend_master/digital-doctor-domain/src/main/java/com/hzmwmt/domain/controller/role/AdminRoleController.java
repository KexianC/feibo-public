package com.hzmwmt.domain.controller.role;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.AdminRoleCreateDTO;
import com.hzmwmt.domain.dto.AdminRoleUpdateDTO;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.service.role.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@Slf4j
@Validated
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/role")
@Tag(name = "角色管理", description = "用于管理员端的角色增删改查操作")
public class AdminRoleController {

    @Resource
    private AdminRoleService adminRoleService;  // 依赖改为AdminRoleService

    @Operation(summary = "新增角色")
    @PostMapping
    // @OperationLog(operationType = "新增", description = "新增角色")
    public Result<Boolean> save(@Valid @RequestBody AdminRoleCreateDTO request) {
        TRole tRole = new TRole();
        tRole.setName(request.getName());
        tRole.setDescription(request.getDescription());
        boolean result = adminRoleService.save(tRole);
        return Result.success(result);
    }

    @Operation(summary = "根据ID删除角色")
    @DeleteMapping("/{id}")
    // @OperationLog(operationType = "删除", description = "根据ID删除角色")
    public Result<Boolean> removeById(@PathVariable Long id) {
        boolean result = adminRoleService.removeById(id);
        return Result.success(result);
    }

    @Operation(summary = "批量删除角色")
    @DeleteMapping("/batch")
    // @OperationLog(operationType = "删除", description = "批量删除角色")
    public Result<Boolean> removeBatchByIds(@RequestBody List<Long> ids) {
        boolean result = adminRoleService.removeBatchByIds(ids);
        return Result.success(result);
    }

    @Operation(summary = "修改角色")
    @PutMapping
    // @OperationLog(operationType = "修改", description = "修改角色")
    public Result<Boolean> updateById(@Valid @RequestBody AdminRoleUpdateDTO request) {
        TRole tRole = new TRole();
        tRole.setId(request.getId());
        tRole.setName(request.getName());
        tRole.setDescription(request.getDescription());
        boolean result = adminRoleService.updateById(tRole);
        return Result.success(result);
    }

    @Operation(summary = "根据ID查询角色")
    @GetMapping("/{id}")
    // @OperationLog(operationType = "查询", description = "根据ID查询角色")
    public Result<TRole> getById(@PathVariable Long id) {
        TRole role = adminRoleService.getById(id);
        return Result.success(role);
    }

    @Operation(summary = "查询所有角色")
    @GetMapping
    // @OperationLog(operationType = "查询", description = "查询所有角色")
    public Result<List<TRole>> list() {
        List<TRole> roles = adminRoleService.list();
        return Result.success(roles);
    }

    @Operation(summary = "分页查询角色")
    @GetMapping("/page")
    // @OperationLog(operationType = "查询", description = "分页查询角色")
    public Result<Page<TRole>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "角色代码") @RequestParam(required = false) @Size(max = 64) String name) {
        var query = Wrappers.<TRole>lambdaQuery();
        if (name != null && !name.isBlank()) {
            query.like(TRole::getName, name.trim());
        }
        Page<TRole> page = adminRoleService.page(new Page<>(pageNum, pageSize), query);
        return Result.success(page);
    }
}
