package com.hzmwmt.domain.controller.frontend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.AuthService;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.entity.function.TUserDigitalModelMap;
import com.hzmwmt.domain.service.function.AdminDigitalModelService;
import com.hzmwmt.domain.service.function.TUserDigitalModelMapService;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/digital-human/model")
@Tag(name = "数字人模型管理接口", description = "用于数字人模型的查询操作")
public class FrontendAdminDigitalModelController {

    @Resource
    private AdminDigitalModelService adminDigitalModelService;

    @Resource
    private AuthService authService;

    @Resource
    private TUserDigitalModelMapService userDigitalModelMapService;

    /**
     * 通过token获取用户ID，查询用户绑定的数字人模型
     */
    @Operation(summary = "查询用户绑定的数字人模型", description = "通过token获取用户ID，查询用户绑定的数字人模型列表")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，默认1", example = "1"),
            @Parameter(name = "pageSize", description = "每页数量，默认10", example = "10")
    })
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回指定页码的数字人模型列表",
            content = @Content(schema = @Schema(implementation = TAdminDigitalModel.class))
    )
    @OperationLog(operationType = "查询", description = "分页查询用户绑定的数字人模型")
    @GetMapping("/index")
    public Result<List<TAdminDigitalModel>> queryUserBoundModels(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize
    ) {
        try {
            log.info("查询用户绑定的数字人模型, pageNum: {}, pageSize: {}", pageNum, pageSize);

            // 从token中获取用户信息
            AuthUserEntity userEntity = authService.queryValidatedUser();
            Long userId = userEntity.getUserId();
            log.info("从token中获取到的用户ID: {}", userId);

            // 查询用户绑定的所有模型映射关系
            QueryWrapper<TUserDigitalModelMap> mapWrapper = new QueryWrapper<>();
            mapWrapper.eq("user_id", userId);
            List<TUserDigitalModelMap> allModelMaps = userDigitalModelMapService.list(mapWrapper);

            if (allModelMaps == null || allModelMaps.isEmpty()) {
                log.info("用户ID: {} 未绑定任何数字人模型", userId);
                return Result.success(List.of());
            }

            // 计算分页参数
            int startIndex = (pageNum - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, allModelMaps.size());

            // 检查是否超出范围
            if (startIndex >= allModelMaps.size()) {
                log.info("分页参数超出范围，返回空列表");
                return Result.success(List.of());
            }

            // 获取当前页的模型映射关系
            List<TUserDigitalModelMap> pageModelMaps = allModelMaps.subList(startIndex, endIndex);

            // 提取数字模型ID列表
            List<String> modelIds = pageModelMaps.stream()
                    .map(TUserDigitalModelMap::getDigitalModelId)
                    .collect(Collectors.toList());

            log.info("用户绑定的数字模型ID列表: {}", modelIds);

            // 根据数字模型ID查询模型实体
            // 注意：TUserDigitalModelMap.digitalModelId映射的是TAdminDigitalModel的id字段
            QueryWrapper<TAdminDigitalModel> wrapper = new QueryWrapper<>();
            wrapper.in("id", modelIds).orderByAsc("sort_num");
            List<TAdminDigitalModel> models = adminDigitalModelService.list(wrapper);

            log.info("第{}页查询到的数字人模型数量: {}", pageNum, models.size());
            return Result.success(models);
        } catch (Exception e) {
            log.error("查询用户绑定的数字人模型异常，异常类型: {}", e.getClass().getSimpleName());
            return Result.error("查询失败，请稍后重试");
        }
    }
}
