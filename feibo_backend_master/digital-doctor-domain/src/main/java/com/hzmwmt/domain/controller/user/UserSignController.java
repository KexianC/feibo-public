package com.hzmwmt.domain.controller.user;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.service.user.UserSignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/sign")
@Slf4j
@Tag(name = "用户签到管理", description = "用户签到管理")
public class UserSignController {

    @Resource
    private UserSignService userSignService;
    @Resource
    private IAuthService authService;

    @Operation(summary = "用户签到")
    @PostMapping()
    public Result<String> sign() {
        userSignService.sign(currentUserId());
        return Result.success("签到成功");
    }

    @GetMapping("/check")
    @Operation(summary = "检查用户签到状态", description = "今日是否需要签到，打开网页时调用，true代表需要签到")
    public Result<Boolean> checkSign() {
        return Result.success(userSignService.checkSign(currentUserId()));
    }

    @Operation(summary = "统计本月签到天数排名", description = "统计本月签到天数排名")
    @GetMapping("/rank")
    public Result<List<Map<String, Object>>> rank() {
        return Result.success(userSignService.rank());
    }

    private Long currentUserId() {
        return authService.queryValidatedUser().getUserId();
    }
}
