package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.third_party_avatar.ThirdPartyAvatarRecordDTO;
import com.hzmwmt.domain.dto.third_party_avatar.ThirdPartyAvatarResponseDTO;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.handler.DigitalModelDataHandler;
import com.hzmwmt.domain.service.function.AdminDigitalModelService;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.hzmwmt.domain.dto.AdminMaterialWriteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/digital-model")
@Tag(name = "数字人模型管理", description = "用于管理员端的数字人模型增删改查操作")
public class AdminDigitalModelController {

    @Resource
    private DigitalModelDataHandler dataHandler;
    @Resource
    private AdminDigitalModelService adminDigitalModelService;

    @Resource
    @Qualifier("digitalWebClient")
    private WebClient webClient;

    /**
     * 上传JSON数据并保存到数据库
     */
    @Operation(summary = "上传数字人模型JSON数据", description = "接收JSON字符串，解析后保存到t_admin_digital_model表")
    @ApiResponse(
            responseCode = "200",
            description = "上传成功",
            content = @Content(schema = @Schema(example = "{\"code\":200, \"message\":\"JSON数据保存成功\"}"))
    )
    @OperationLog(operationType = "新增", description = "上传数字人模型JSON数据")
    @PostMapping("/upload-json")
    public Result<Boolean> uploadJson(
            @Parameter(description = "包含数字人模型数据的JSON字符串（需与示例格式一致）", required = true)
            @RequestBody String jsonData
    ) {
        try {
            // 调用解析并保存的方法
            dataHandler.parseAndSave(jsonData);
            return Result.success("JSON数据保存成功", true);
        } catch (Exception e) {
            // 异常处理（如JSON格式错误、数据为空等）
            log.error("上传数字人模型JSON失败，异常类型: {}", e.getClass().getSimpleName());
            return Result.error("保存失败，请检查JSON格式或稍后重试");
        }
    }

    @Operation(summary = "分页查询数字人模型", description = "支持姓名、性别、状态多条件筛选")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，默认1", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数，默认10", example = "10"),
            @Parameter(name = "name", description = "姓名模糊搜索（可选）", example = "贺晓鸣"),
            @Parameter(name = "gender", description = "性别筛选（可选）", example = "女"),
            @Parameter(name = "status", description = "状态筛选（可选）", example = "已上线"),
            @Parameter(name = "hospital", description = "医院筛选（可选）", example = "人民医院")
    })
    @OperationLog(operationType = "查询", description = "分页查询数字人模型")
    @GetMapping("/page")
    public Result<IPage<TAdminDigitalModel>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String hospital
    ) {
        IPage<TAdminDigitalModel> resultPage = adminDigitalModelService.pageDigitalModels(pageNum, pageSize, name, gender, status, hospital);
        return Result.success(resultPage);
    }

    @Operation(summary = "新增数字人模型", description = "传入完整模型信息（含头像、姓名等）")
    @OperationLog(operationType = "新增", description = "新增数字人模型")
    @PostMapping
    public Result<Boolean> add(
            @Parameter(description = "数字人模型信息", required = true)
            @RequestBody TAdminDigitalModel digitalModel
    ) {
        boolean result = adminDigitalModelService.save(digitalModel);
        return Result.success(result);
    }

    @Operation(summary = "根据ID查询数字人模型", description = "获取单条模型的完整信息")
    @OperationLog(operationType = "查询", description = "根据ID查询数字人模型")
    @GetMapping("/{id}")
    public Result<TAdminDigitalModel> getById(
            @Parameter(description = "数字人模型ID", example = "59")
            @PathVariable Long id
    ) {
        TAdminDigitalModel model = adminDigitalModelService.getById(id);
        return Result.success(model);
    }

    @Operation(summary = "更新数字人模型", description = "传入含ID的模型信息，支持修改姓名、状态等字段")
    @OperationLog(operationType = "修改", description = "更新数字人模型")
    @PutMapping
    public Result<Boolean> update(
            @Parameter(description = "数字人模型信息（必须包含ID）", required = true)
            @Valid @RequestBody AdminMaterialWriteRequest.ModelUpdate request
    ) {
        TAdminDigitalModel digitalModel = new TAdminDigitalModel();
        digitalModel.setId(request.id());
        digitalModel.setModelName(request.modelName().trim());
        digitalModel.setSortNum(request.sortNum());
        digitalModel.setStatus(request.status());
        digitalModel.setLastUpdateTime(LocalDateTime.now());
        boolean result = adminDigitalModelService.updateById(digitalModel);
        return Result.success(result);
    }

    @Operation(summary = "删除数字人模型（逻辑删除）", description = "标记模型为「已删除」，不真正删除数据")
    @OperationLog(operationType = "删除", description = "删除数字人模型")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(
            @Parameter(description = "数字人模型ID", example = "59")
            @PathVariable Long id
    ) {
        boolean result = adminDigitalModelService.removeById(id);
        return Result.success(result);
    }

    /**
     * 从第三方API更新数字人模型
     */
    @Operation(summary = "从第三方API更新数字人模型", description = "从外部API获取最新数字人模型数据并更新数据库")
    @ApiResponse(
            responseCode = "200",
            description = "更新成功",
            content = @Content(schema = @Schema(example = "{\"code\":200, \"message\":\"更新成功，新增/更新了10条数据\"}"))
    )
    @OperationLog(operationType = "更新", description = "从第三方API更新数字人模型")
    @PostMapping("/update-from-third-party")
    public Result<String> updateFromThirdParty() {
        try {
            // 调用第三方API获取数据
            String thirdPartyUrl = "https://openman.weta365.com/metaman/open/avatar/infos?customType=muwumutong";

            log.info("开始从第三方API获取数字人模型数据");

            // 使用WebClient调用第三方API
            ThirdPartyAvatarResponseDTO response = webClient
                    .get()
                    .uri(thirdPartyUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(ThirdPartyAvatarResponseDTO.class)
                    .block(); // 同步调用

            if (response == null || response.getCode() != 200 || response.getData() == null) {
                log.error("第三方API返回数据异常，响应码: {}", response == null ? null : response.getCode());
                return Result.error("获取第三方数据失败，请稍后重试");
            }

            log.info("成功获取第三方数据，共{}条记录", response.getData().getRecords().size());

            // 处理数据并更新数据库
            AtomicInteger successCount = new AtomicInteger(0);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (ThirdPartyAvatarRecordDTO record : response.getData().getRecords()) {
                try {
                    // 检查数据库中是否已存在该模型
                    TAdminDigitalModel existingModel = adminDigitalModelService.getById(record.getId());
                    TAdminDigitalModel model;

                    if (existingModel != null) {
                        // 更新现有模型
                        model = existingModel;
                        log.info("更新现有模型，模型ID: {}", record.getId());
                    } else {
                        // 创建新模型
                        model = new TAdminDigitalModel();
                        model.setId(record.getId());
                        log.info("新增模型，模型ID: {}", record.getId());
                    }

                    // 设置模型属性
                    model.setModelName(record.getSpeakerName());
                    model.setModelId(record.getSpeakerId());
                    model.setModelType(record.getSpeakerType());
                    model.setModelWidth(record.getSpeakerWidth());
                    model.setModelHeight(record.getSpeakerHeight());
                    model.setModelUrl(record.getSpeakerUrl());
                    model.setModelModelType(record.getSpeakerModelType());
                    model.setGender(record.getGender());
                    model.setMagicAudioId(record.getMagicAudioId());
                    model.setHeadx(record.getHeadx());
                    model.setHeady(record.getHeady());
                    model.setHeadr(record.getHeadr());
                    model.setHeadUrl(record.getHeadUrl());
                    model.setHeadWidth(record.getHeadWidth());
                    model.setHeadHeight(record.getHeadHeight());
                    model.setCustomType(record.getCustomType());
                    model.setStatus(record.getStatus());
                    model.setModelDemoUrl(record.getSpeakerDemoUrl());

                    // 处理日期时间 - 增加容错处理
                    if (record.getCreated() != null) {
                        try {
                            model.setCreated(LocalDateTime.parse(record.getCreated(), formatter));
                        } catch (Exception e) {
                            log.warn("模型 created 日期格式解析失败，模型ID: {}", record.getId());
                            // 设置为当前时间作为备用
                            model.setCreated(LocalDateTime.now());
                        }
                    } else {
                        model.setCreated(LocalDateTime.now());
                    }

                    if (record.getLastUpdateTime() != null) {
                        try {
                            model.setLastUpdateTime(LocalDateTime.parse(record.getLastUpdateTime(), formatter));
                        } catch (Exception e) {
                            log.warn("模型 lastUpdateTime 日期格式解析失败，模型ID: {}", record.getId());
                            // 设置为当前时间作为备用
                            model.setLastUpdateTime(LocalDateTime.now());
                        }
                    } else {
                        model.setLastUpdateTime(LocalDateTime.now());
                    }

                    // 设置is_deleted字段默认值
                    model.setIsDeleted(0);

                    // 保存到数据库
                    log.info("准备保存模型到数据库，id={}", model.getId());
                    boolean saveResult = adminDigitalModelService.saveOrUpdate(model);
                    if (saveResult) {
                        log.info("保存成功，id={}", model.getId());
                        successCount.incrementAndGet();
                    } else {
                        log.error("保存失败，id={}", model.getId());
                    }
                } catch (Exception e) {
                    log.error("处理模型数据失败，模型ID: {}，异常类型: {}",
                            record.getId(), e.getClass().getSimpleName());
                }
            }

            String message = String.format("更新成功，新增/更新了%d条数据", successCount.get());
            log.info(message);
            return Result.success(message);

        } catch (Exception e) {
            log.error("从第三方API更新数字人模型失败，异常类型: {}", e.getClass().getSimpleName());
            return Result.error("更新失败，请稍后重试");
        }
    }


}
