package com.hzmwmt.domain.controller.user;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.user.TFeedback;
import com.hzmwmt.domain.service.user.AdminFeedbackService;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @Validated
@PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/user/feedback")
public class AdminFeedbackController {
    @Resource private AdminFeedbackService adminFeedbackService;
    @Resource private IAuthService authService;
    public record Action(@NotNull @Min(0) Long version,@Size(max=5000) String reply,@Min(0) @Max(3) Integer priority) {}
    @GetMapping("/page")
    public Result<IPage<TFeedback>> page(@RequestParam(defaultValue="1") @Min(1) Integer pageNum,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) Integer pageSize,
        @RequestParam(required=false) Long userId,@RequestParam(required=false) Long type,@RequestParam(required=false) String keyword,
        @RequestParam(required=false) @Min(0) @Max(3) Integer status,@RequestParam(required=false) @Min(0) @Max(3) Integer priority,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminFeedbackService.pageQuery(pageNum,pageSize,userId,type,keyword,status,priority,startDate,endDate));
    }
    @PostMapping("/{id}/reply") @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="处理",description="保存反馈回复",recordParams=false,recordResult=false,targetIdParameterIndex=0,critical=true)
    public Result<TFeedback> reply(@PathVariable @Positive Long id,@Valid @RequestBody Action action) {
        return Result.success(adminFeedbackService.process(id,action.version(),1,action.reply(),action.priority(),authService.queryValidatedUser().getUserId()));
    }
    @PostMapping("/{id}/resolve") @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="处理",description="解决用户反馈",recordParams=false,recordResult=false,targetIdParameterIndex=0,critical=true)
    public Result<TFeedback> resolve(@PathVariable @Positive Long id,@Valid @RequestBody Action action) {
        return Result.success(adminFeedbackService.process(id,action.version(),2,null,null,authService.queryValidatedUser().getUserId()));
    }
    @PostMapping("/{id}/close") @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="处理",description="关闭用户反馈",recordParams=false,recordResult=false,targetIdParameterIndex=0,critical=true)
    public Result<TFeedback> close(@PathVariable @Positive Long id,@Valid @RequestBody Action action) {
        return Result.success(adminFeedbackService.process(id,action.version(),3,null,null,authService.queryValidatedUser().getUserId()));
    }
}
