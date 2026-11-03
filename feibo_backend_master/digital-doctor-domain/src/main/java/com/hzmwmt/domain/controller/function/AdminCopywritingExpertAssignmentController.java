package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.service.function.AdminCopywritingExpertAssignmentService;
import com.hzmwmt.domain.vo.AdminCopywritingExpertAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewExpertOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/copywriting-expert-assignment")
@Tag(name = "Copywriting expert assignment")
public class AdminCopywritingExpertAssignmentController {

    @Resource
    private AdminCopywritingExpertAssignmentService assignmentService;

    @GetMapping("/accounts")
    @Operation(summary = "Query accounts available for expert assignment")
    public Result<IPage<AdminReviewAccountOptionVO>> queryAccounts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(assignmentService.queryAccounts(page, size, keyword));
    }

    @PostMapping("/query")
    @Operation(summary = "Query expert assignments for accounts")
    public Result<List<AdminCopywritingExpertAssignmentVO>> queryAssignments(
            @RequestBody List<Long> accountUserIds) {
        return Result.success(assignmentService.queryAssignments(accountUserIds));
    }

    @GetMapping("/experts")
    @Operation(summary = "Query active review experts")
    public Result<List<AdminReviewExpertOptionVO>> queryExpertOptions(
            @RequestParam(required = false) String keyword) {
        return Result.success(assignmentService.queryExpertOptions(keyword));
    }

    @PutMapping("/{accountUserId}")
    @Operation(summary = "Replace all review experts assigned to an account")
    public Result<List<AdminCopywritingExpertAssignmentVO>> replaceAssignments(
            @PathVariable Long accountUserId,
            @RequestBody List<Long> expertUserIds) {
        return Result.success(assignmentService.replaceAssignments(accountUserId, expertUserIds));
    }
}
