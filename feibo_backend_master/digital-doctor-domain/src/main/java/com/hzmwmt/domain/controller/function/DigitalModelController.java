package com.hzmwmt.domain.controller.function;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.service.function.DigitalModelService;
import com.hzmwmt.domain.vo.DigitalModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数字模型控制器
 *
 * @author AI Assistant
 */
@Tag(name = "数字模型管理", description = "数字模型相关接口")
@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/digital-model")
@Validated
public class DigitalModelController {

    @Autowired
    private DigitalModelService digitalModelService;

    /**
     * 查询所有数字模型信息
     *
     * @return 数字模型列表
     */
    @Operation(summary = "查询所有数字模型", description = "获取所有数字模型的基本信息，包括模型名称、模型ID和模型URL")
    @GetMapping("/page")
    public Result<List<DigitalModelVO>> queryAllDigitalModels() {
        List<DigitalModelVO> digitalModels = digitalModelService.queryAllDigitalModels();
        return Result.success(digitalModels);
    }

    /**
     * 根据用户ID查询绑定的数字模型列表
     *
     * @param userId 用户ID
     * @return 用户绑定的数字模型列表
     */
    @Operation(summary = "根据用户ID查询绑定模型", description = "根据用户ID查询该用户已绑定的所有数字模型信息")
    @GetMapping("/user-bound")
    public Result<List<DigitalModelVO>> queryUserBoundModels(@RequestParam(required = true) @Schema(description = "用户ID", example = "700853889") Long userId) {
        List<DigitalModelVO> boundModels = digitalModelService.queryUserBoundModels(userId);
        return Result.success(boundModels);
    }

    /**
     * 修改用户和数字模型的绑定关系
     * 逻辑：先删除用户的全部绑定关系，然后添加新的绑定关系
     *
     * @param userId   用户ID
     * @param modelIds 要绑定的数字模型ID列表
     * @return 修改结果
     */
    @Operation(summary = "修改用户模型绑定关系", description = "修改用户和数字模型的绑定关系，先删除全部现有绑定，再添加新绑定")
    @PostMapping("/user-bindings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Boolean> updateUserModelBindings(
            @RequestParam(required = true) @Positive @Schema(description = "用户ID", example = "700853889") Long userId,
            @RequestParam(required = false) @Size(max = 100) @Schema(description = "要绑定的数字模型ID列表", example = "[1,2,3]") List<@Positive Long> modelIds) {
        boolean result = digitalModelService.updateUserModelBindings(userId, modelIds);
        return result ? Result.success(true) : Result.error("修改绑定关系失败", false);
    }
}
