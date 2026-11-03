package com.hzmwmt.domain.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.AdminUserUpdateDTO;
import com.hzmwmt.domain.dto.AdminUserStatusUpdateDTO;
import com.hzmwmt.domain.dto.UserCreateRequest;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.service.user.AdminModelConfigService;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.domain.vo.AdminModelConfigVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;


/**
 * 管理员用户管理控制器
 * （@Tag：标注控制器功能，在文档中作为模块标题）
 */
@Tag(name = "管理员用户管理", description = "用于管理员端的用户查询、修改、禁用及AI模型查询等操作")
@Slf4j
@Validated
@RestController
@RequestMapping("/api/admin/user/users")
public class AdminUserController {

    @Resource
    private AdminUserService adminUserService;
    @Resource
    private AdminModelConfigService adminModelConfigService;

    /**
     * 分页查询用户信息
     * （@Operation：描述接口功能；@Parameters：批量定义参数说明）
     */
    @Operation(summary = "分页查询用户列表", description = "支持按关键词（用户名/手机号）模糊查询、状态筛选，返回分页用户数据")
    @Parameters({
            @Parameter(name = "page", description = "页码，默认第1页", example = "1", required = false),
            @Parameter(name = "size", description = "每页条数，默认10条", example = "10", required = false),
            @Parameter(name = "keyword", description = "搜索关键词（支持用户名、手机号模糊匹配）", example = "张三", required = false),
            @Parameter(name = "status", description = "用户状态（如启用/禁用，具体值参考业务字典）", example = "1", required = false),
            @Parameter(name = "oderByUpdate", description = "更新时间排序（0=升序，1=降序）", example = "0", required = false),
            @Parameter(name = "oderByCreate", description = "创建时间排序（0=升序，1=降序）", example = "1", required = false)
    })
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
    // @OperationLog(operationType = "查询", description = "分页查询用户")
    public Result<Page<TUser>> page(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) @Size(max = 64) String keyword,
            @RequestParam(required = false) @Pattern(regexp = "true|false|0|1") String status,
            @RequestParam(required = false) @Min(0) @Max(1) Integer oderByUpdate,
            @RequestParam(required = false) @Min(0) @Max(1) Integer oderByCreate
    ) {
        Page<TUser> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<TUser> queryWrapper = Wrappers.lambdaQuery();

        // 处理查询条件
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(group -> group
                    .like(TUser::getNickName, keyword)
                    .or()
                    .like(TUser::getPhoneNumber, keyword));
        }
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq(TUser::getStatus, status);
        }
        if (oderByUpdate != null) {
            if (oderByUpdate == 1) {
                queryWrapper.orderByDesc(TUser::getUpdateTime);
            } else if (oderByUpdate == 0) {
                queryWrapper.orderByAsc(TUser::getUpdateTime);
            } else {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }
        }
        if (oderByCreate != null) {
            if (oderByCreate == 1) {
                queryWrapper.orderByDesc(TUser::getCreateTime);
            } else if (oderByCreate == 0) {
                queryWrapper.orderByAsc(TUser::getCreateTime);
            } else {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }
        }

        Page<TUser> result = adminUserService.page(pageInfo, queryWrapper);
        return Result.success(result);
    }

    /**
     * 查询可用AI模型列表
     */
    @Operation(summary = "查询可用AI模型列表", description = "返回系统中配置的所有可用AI模型信息（如模型名称、类型等）")
    @GetMapping("/models")
    @PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
    // @OperationLog(operationType = "查询", description = "查询可用AI模型列表")
    public Result<List<AdminModelConfigVO>> getAvailableModels() {
        List<AdminModelConfigVO> adminModelConfigs = adminModelConfigService.getAdminModelConfigs();
        return Result.success(adminModelConfigs);
    }

    /**
     * 根据ID查询用户信息（用于修改前回显）
     */
    @Operation(summary = "根据ID查询用户详情", description = "查询指定ID的用户完整信息，用于修改操作的表单回显")
    @Parameter(name = "id", description = "用户ID（主键）", required = true, example = "1001")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
    // @OperationLog(operationType = "查询", description = "根据ID查询用户详情")
    public Result<TUser> getUserInfo(@PathVariable Long id) {
        log.info("根据ID查询用户详情，ID：{}", id);
        Optional<TUser> userOptional = adminUserService.getUserById(id);

        if (userOptional.isPresent()) {
            return Result.success(userOptional.get());
        } else {
            return Result.error("用户不存在或ID无效");
        }
    }

    /**
     * 修改用户信息
     */
    @Operation(summary = "修改用户信息", description = "根据用户ID更新用户信息（如昵称、状态等，具体字段参考AdminUserUpdateDTO）并返回更新后的用户信息")
    @Parameters({
            @Parameter(name = "id", description = "用户ID（主键，必须与DTO中的用户ID一致）", required = true, example = "1001"),
            @Parameter(name = "updateDTO", description = "用户更新参数（包含需要修改的字段）", required = true)
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    // @OperationLog(operationType = "修改", description = "修改用户信息")
    public Result<TUser> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateDTO updateDTO) {

        log.info("修改用户信息，ID：{}", id);
        boolean success = adminUserService.updateUser(id, updateDTO);

        if (success) {
            // 更新成功后查询最新的用户信息并返回
            Optional<TUser> updatedUserOptional = adminUserService.getUserById(id);
            if (updatedUserOptional.isPresent()) {
                return Result.success(updatedUserOptional.get());
            }
            return Result.error("用户更新成功但查询失败");
        } else {
            return Result.error("修改失败（用户不存在或参数无效）");
        }
    }

    /**
     * 逻辑删除用户（禁用用户）
     */
    @Operation(summary = "设置用户启用状态", description = "显式设置用户是否禁用；重复请求保持幂等，不执行反向切换")
    @Parameter(name = "id", description = "用户ID（主键）", required = true, example = "1001")
    @PutMapping("/status/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    // @OperationLog(operationType = "删除", description = "禁用用户")
    public Result<Boolean> setUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusUpdateDTO request) {
        log.info("设置用户禁用状态，ID：{}", id);
        boolean success = adminUserService.setUserDisabled(id, request.getDisabled());

        if (success) {
            return Result.success(true);
        } else {
            return Result.error("启用或禁用失败（用户不存在或ID无效）");
        }
    }

    /**
     * 批量新增用户
     *
     * @param file excel表格
     * @return
     */
    @Operation(summary = "批量新增用户", description = "通过excel导入用户数据")
    @PostMapping("/creatByExcel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Parameter(name = "file", description = "Excel文件", required = true)
    public Result<List<String>> createUserByExcel(@RequestParam("file") MultipartFile file) throws IOException {
        List<String> errorLogs = adminUserService.createUserByExcel(file);
        return Result.success(errorLogs);
    }


    /**
     * 新增用户接口
     * 必传参数：nick_name, phone_number
     * 可选参数：role_id（默认使用医生角色ID：1964218007207526401）
     */
    @Operation(summary = "新增用户", description = "创建新用户，支持指定角色，默认使用医生角色")
    @Parameters({
            @Parameter(name = "request", description = "用户创建请求参数", required = true,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UserCreateRequest.class))
    })
    @PostMapping("/create")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<TUser> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("超级管理员发起后台用户创建");

        try {
            // 创建用户实体
            TUser user = new TUser();
            user.setNickName(request.getNickName());
            user.setPhoneNumber(request.getPhoneNumber());

            // 设置可选参数（如果有）
            if (request.getGender() != null) {
                user.setGender(request.getGender());
            }
            if (request.getOpenId() != null) {
                user.setOpenId(request.getOpenId());
            }

            // 调用服务层创建用户，支持指定角色ID
            TUser createdUser = adminUserService.createUser(user, request.getRoleId());

            return Result.success(createdUser);
        } catch (Exception e) {
            log.error("新增用户失败，异常类型: {}", e.getClass().getSimpleName());
            return Result.error("新增用户失败，请稍后重试");
        }
    }
}
