package com.hzmwmt.domain.controller.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.entity.log.TLoginLog;
import com.hzmwmt.domain.service.log.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户登录日志控制器
 * 提供登录日志的分页查询和新增功能
 */
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/log/login-logs")
@Tag(name = "用户登录日志管理", description = "用于管理用户登录日志的接口")
public class LoginLogController {

    @Resource
    private LoginLogService loginLogService;

    /**
     * 分页查询登录日志
     * 支持按用户名、IP、状态等条件筛选
     */
    @Operation(summary = "分页查询登录日志", description = "支持按用户名、IP、登录状态等条件筛选")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码（默认1）", example = "1", in = ParameterIn.QUERY),
            @Parameter(name = "pageSize", description = "每页条数（默认10）", example = "10", in = ParameterIn.QUERY),
            @Parameter(name = "loginUser", description = "登录用户名（模糊匹配）", example = "admin", in = ParameterIn.QUERY),
            @Parameter(name = "loginIp", description = "登录IP（模糊匹配）", example = "192.168.1.1", in = ParameterIn.QUERY),
            @Parameter(name = "loginStatus", description = "登录状态", example = "登录成功", in = ParameterIn.QUERY),
            @Parameter(name = "startTime", description = "开始时间（格式：yyyy-MM-dd HH:mm:ss）", example = "2024-01-01 00:00:00", in = ParameterIn.QUERY),
            @Parameter(name = "endTime", description = "结束时间（格式：yyyy-MM-dd HH:mm:ss）", example = "2024-12-31 23:59:59", in = ParameterIn.QUERY)
    })
    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Page.class)))
    @GetMapping("/page")
    public Page<TLoginLog> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String loginUser,
            @RequestParam(required = false) String loginIp,
            @RequestParam(required = false) String loginStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        // 使用自定义的pageQuery方法，支持完整的条件查询
        return loginLogService.pageQuery(pageNum, pageSize, loginUser, loginIp, loginStatus, startTime, endTime);
    }

}
