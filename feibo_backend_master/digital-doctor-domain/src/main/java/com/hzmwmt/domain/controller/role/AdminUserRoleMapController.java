package com.hzmwmt.domain.controller.role;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.AdminUserRoleMapCreateDTO;
import com.hzmwmt.domain.dto.AdminUserRoleMapUpdateDTO;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.service.order.AdminUserRoleMapService;
import com.hzmwmt.domain.vo.role.AdminUserRoleMapVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/user-role-map")
@Tag(name = "用户角色映射管理", description = "用于管理员端的用户角色映射增删改查操作")
public class AdminUserRoleMapController {

    @Resource
    private AdminUserRoleMapService adminUserRoleMapService;  // 依赖改为AdminUserRoleMapService

    @Operation(summary = "新增用户角色映射")
    @PostMapping
    // @OperationLog(operationType = "新增", description = "新增用户角色映射")
    public Result<Boolean> save(@Valid @RequestBody AdminUserRoleMapCreateDTO request) {
        TUserRoleMap userRoleMap = new TUserRoleMap();
        userRoleMap.setUserId(request.getUserId());
        userRoleMap.setRoleId(request.getRoleId());
        boolean result = adminUserRoleMapService.save(userRoleMap);
        return Result.success(result);
    }

    @Operation(summary = "根据ID删除用户角色映射")
    @DeleteMapping("/{id}")
    // @OperationLog(operationType = "删除", description = "根据ID删除用户角色映射")
    public Result<Boolean> removeById(@PathVariable @Positive Integer id) {
        boolean result = adminUserRoleMapService.removeById(id);
        return Result.success(result);
    }

    @Operation(summary = "批量删除用户角色映射")
    @DeleteMapping("/batch")
    // @OperationLog(operationType = "删除", description = "批量删除用户角色映射")
    public Result<Boolean> removeBatchByIds(
            @RequestBody @Size(min = 1, max = 100) List<@Positive Integer> ids) {
        boolean result = adminUserRoleMapService.removeBatchByIds(ids);
        return Result.success(result);
    }

    @Operation(summary = "修改用户角色映射")
    @PutMapping
    // @OperationLog(operationType = "修改", description = "修改用户角色映射")
    public Result<Boolean> updateById(@Valid @RequestBody AdminUserRoleMapUpdateDTO request) {
        TUserRoleMap userRoleMap = new TUserRoleMap();
        userRoleMap.setId(request.getId());
        userRoleMap.setUserId(request.getUserId());
        userRoleMap.setRoleId(request.getRoleId());
        boolean result = adminUserRoleMapService.updateById(userRoleMap);
        return Result.success(result);
    }

    @Operation(summary = "根据ID查询用户角色映射")
    @GetMapping("/{id}")
    // @OperationLog(operationType = "查询", description = "根据ID查询用户角色映射")
    public Result<TUserRoleMap> getById(@PathVariable @Positive Integer id) {
        TUserRoleMap roleMap = adminUserRoleMapService.getById(id);
        return Result.success(roleMap);
    }

    @Operation(summary = "查询所有用户角色映射")
    @GetMapping
    // @OperationLog(operationType = "查询", description = "查询所有用户角色映射")
    public Result<List<TUserRoleMap>> list() {
        List<TUserRoleMap> roleMaps = adminUserRoleMapService.list();
        return Result.success(roleMaps);
    }

    @Operation(summary = "分页查询用户角色映射（含用户昵称和角色名）")
    @GetMapping("/page")
    // @OperationLog(operationType = "查询", description = "分页查询用户角色映射")
    public Result<Page<AdminUserRoleMapVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            // 按用户昵称搜索（对应t_user.nick_name）
            @RequestParam(required = false) @Size(max = 64) String nickName,
            // 按角色名搜索（对应t_role.name）
            @RequestParam(required = false) @Size(max = 64) String roleName
    ) {
        Page<AdminUserRoleMapVO> page = new Page<>(pageNum, pageSize);

        QueryWrapper<AdminUserRoleMapVO> queryWrapper = new QueryWrapper<>();
        // 按用户昵称模糊查询（关联t_user.nick_name）
        if (nickName != null && !nickName.isEmpty()) {
            queryWrapper.like("u.nick_name", nickName);
        }
        // 按角色名模糊查询
        if (roleName != null && !roleName.isEmpty()) {
            queryWrapper.like("r.name", roleName);
        }
        queryWrapper.orderByDesc("urm.create_time");

        Page<AdminUserRoleMapVO> resultPage = (Page<AdminUserRoleMapVO>) adminUserRoleMapService.selectUserRoleMapVOPage(page, queryWrapper);
        return Result.success(resultPage);
    }
}
