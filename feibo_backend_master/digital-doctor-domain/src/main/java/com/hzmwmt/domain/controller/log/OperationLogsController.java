package com.hzmwmt.domain.controller.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.service.log.OperationLogsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * 操作日志管理控制器
 * 提供操作日志的分页查询和新增功能
 */
@Tag(name = "操作日志管理", description = "用于操作日志的分页查询和新增操作")
@Slf4j
@Validated
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/log/operation-logs")
public class OperationLogsController {

    @Resource
    private OperationLogsService operationLogsService;

    /**
     * 分页查询操作日志
     */
    @Operation(summary = "分页查询操作日志", description = "支持按操作者、操作方法、时间范围等条件筛选，返回分页日志数据")
    @Parameters({
            @Parameter(name = "page", description = "页码，默认第1页", example = "1", required = false),
            @Parameter(name = "size", description = "每页条数，默认10条", example = "10", required = false),
            @Parameter(name = "operator", description = "操作者", example = "admin", required = false),
            @Parameter(name = "operationMethod", description = "操作方法", example = "login", required = false),
            @Parameter(name = "startTime", description = "开始时间（yyyy-MM-dd HH:mm:ss）", example = "2025-10-01 00:00:00", required = false),
            @Parameter(name = "endTime", description = "结束时间（yyyy-MM-dd HH:mm:ss）", example = "2025-10-31 23:59:59", required = false)
    })
    @GetMapping("/page")
    // @OperationLog(operationType = "查询", description = "分页查询操作日志", recordParams = true)
    public Result<Page<TOperationLogs>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String operationMethod,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        try {
            Page<TOperationLogs> pageInfo = new Page<>(page, size);
            LambdaQueryWrapper<TOperationLogs> queryWrapper = Wrappers.lambdaQuery();

            // 处理查询条件
            if (operator != null && !operator.isEmpty()) {
                queryWrapper.eq(TOperationLogs::getOperator, operator);
            }
            if (operationMethod != null && !operationMethod.isEmpty()) {
                queryWrapper.eq(TOperationLogs::getOperationMethod, operationMethod);
            }
            // 这里暂时注释时间范围查询，实际使用时需要进行日期格式转换
            // if (startTime != null && !startTime.isEmpty()) {
            //     queryWrapper.ge(TOperationLogs::getOperationTime, parseDateTime(startTime));
            // }
            // if (endTime != null && !endTime.isEmpty()) {
            //     queryWrapper.le(TOperationLogs::getOperationTime, parseDateTime(endTime));
            // }

            // 按操作时间倒序排列
            queryWrapper.orderByDesc(TOperationLogs::getOperationTime);

            // 调用服务层进行分页查询
            Page<TOperationLogs> result = operationLogsService.pageQuery(pageInfo, queryWrapper);
            return Result.success(result);
        } catch (Exception e) {
            log.error("分页查询操作日志失败，异常类型: {}", e.getClass().getSimpleName());
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 日期时间解析方法（实际使用时需要实现）
     */
    // private Date parseDateTime(String dateTimeStr) {
    //     // 实际实现时需要使用SimpleDateFormat或DateTimeFormatter进行日期解析
    //     return null;
    // }
}
