package com.hzmwmt.trigger.http.client;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hzmwmt.api.IUserApi;
import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiOffsetPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.user.model.aggregate.UserAIResourceAggregate;
import com.hzmwmt.domain.user.model.aggregate.UserAISubscriptionAggregate;
import com.hzmwmt.domain.user.model.entity.*;
import com.hzmwmt.domain.user.model.valobj.AIFeatureTemplateVO;
import com.hzmwmt.domain.user.service.IUserAssistantService;
import com.hzmwmt.domain.user.service.IUserExpertService;
import com.hzmwmt.domain.user.service.IUserManagerService;
import com.hzmwmt.domain.user.service.IUserResourceService;
import com.hzmwmt.types.annotations.Idempotent;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserController
 * @Date: 2025/9/3 15:46
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/user/")
public class UserController implements IUserApi {

    @Resource
    private IUserResourceService userResourceService;
    @Resource
    private IAuthService authService;
    @Resource
    private IUserExpertService userExpertService;
    @Resource
    private IUserAssistantService userAssistantService;
    @Resource
    private IUserManagerService userManagerService;

    /**
     * 查询用户AI资源首页
     *
     * @return
     */
    @Override
    @RequestMapping(value = "resource/index", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户AI资源首页信息", recordParams = true)
    public Response<UserResourceResponseDTO> queryUserResourceIndex() {
        // 1. 获取当前用户信息并记录日志
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("查询用户 {} 的AI资源首页信息开始...", userId);

        // 2. 调用领域服务，分别获取资源权益和订阅信息
        List<UserAIResourceAggregate> resourceAggregates = userResourceService.queryUserAiResourceIndex(userId);
        List<UserAISubscriptionAggregate> subscriptionAggregates = userResourceService.queryAiMembershipPlanSubscriptionIndex(userId);

        // 3. 准备用于存放最终DTO数据的列表
        List<UserResourceResponseDTO.UserResource> userResourcesDTOs = new ArrayList<>();
        List<UserResourceResponseDTO.UserSubscription> userSubscriptionsDTOs = new ArrayList<>();

        // 4. (清晰的步骤) 转换用户的AI资源权益列表
        if (resourceAggregates != null) {
            for (UserAIResourceAggregate agg : resourceAggregates) {
                UserAIResourceEntity entity = agg.getUserAiResourceEntity();
                AIFeatureTemplateVO templateVO = entity.getAiFeatureTemplateVO();

                // 使用Builder构建扁平化的 UserResource DTO
                UserResourceResponseDTO.UserResource resourceDto = UserResourceResponseDTO.UserResource.builder()
                        .userAiResourceId(entity.getUserAiResourceId())
                        // 直接将templateVO中的字段映射到UserResource DTO
                        .templateCode(templateVO.getTemplateCode())
                        .name(templateVO.getName())
                        .description(templateVO.getDescription())
                        // 从聚合根获取余额，并转换为BigDecimal
                        .subscriptionBalance(agg.getSubscriptionBalance())
                        .purchasedBalance(agg.getPurchasedBalance())
                        .build();

                userResourcesDTOs.add(resourceDto);
            }
        }

        // 5. (清晰的步骤) 转换用户的订阅列表
        if (subscriptionAggregates != null) {
            for (UserAISubscriptionAggregate agg : subscriptionAggregates) {
                // 假设agg中包含getUserAiSubscriptionEntity()等方法
                UserResourceResponseDTO.UserSubscription subscriptionDto = UserResourceResponseDTO.UserSubscription.builder()
                        .userAiSubscriptionId(agg.getUserAiSubscriptionEntity().getUserAiSubscriptionId())
                        .name(agg.getName())
                        .planCode(agg.getPlanCode())
                        .startTime(agg.getUserAiSubscriptionEntity().getStartTime())
                        .endTime(agg.getUserAiSubscriptionEntity().getEndTime())
                        .build();

                userSubscriptionsDTOs.add(subscriptionDto);
            }
        }

        // 6. 组装最终的响应数据
        UserResourceResponseDTO responseDTO = UserResourceResponseDTO.builder()
                .userResources(userResourcesDTOs)
                .userSubscriptions(userSubscriptionsDTOs)
                .build();

        log.info("查询用户 {} 的AI资源首页信息成功, 返回 {} 条资源权益, {} 条订阅信息。", userId, userResourcesDTOs.size(), userSubscriptionsDTOs.size());

        // 7. 返回标准响应体
        return Response.<UserResourceResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responseDTO)
                .build();
    }

    @Override
    @RequestMapping(value = "expert/assistants", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询专家的助理列表", recordParams = true)
    public Response<ApiOffsetPageData<QueryAssistantsResponseDTO>> queryAssistants(
            @RequestParam(name = "page")
            Integer page,
            @RequestParam(name = "size")
            Integer size) {

        Long expertId = authService.queryValidatedUser().getUserId();
        log.info("专家 {} 开始查询助理列表, page: {}, size: {}", expertId, page, size);

        OffsetPageResponseData<UserEntity> domainPage = userExpertService.queryAssistantConfig(expertId, page, size);

        if (domainPage.getData() == null) {
            return Response.<ApiOffsetPageData<QueryAssistantsResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(
                            ApiOffsetPageData.<QueryAssistantsResponseDTO>builder()
                                    .hasNext(false)
                                    .total(domainPage.getTotal())
                                    .size(0)
                                    .page(domainPage.getPage())
                                    .data(null)
                                    .build()
                    ).build();
        }
        // 将 UserEntity 列表转换为 【专属DTO】QueryAssistantsResponseDTO 列表
        List<QueryAssistantsResponseDTO> dtoList = domainPage.getData().stream()
                .map(userEntity -> QueryAssistantsResponseDTO.builder()
                        .userId(userEntity.getUserId())
                        .nickName(userEntity.getNickName())
                        .avatar(userEntity.getAvatar())
                        .phoneNumber(userEntity.getPhoneNumber())
                        .gender(userEntity.getGenderEnumVO().getDescription())
                        .build())
                .collect(Collectors.toList());

        ApiOffsetPageData<QueryAssistantsResponseDTO> responsePage = new ApiOffsetPageData<>(
                dtoList, domainPage.getHasNext(), domainPage.getTotal(), domainPage.getPage(), domainPage.getSize()
        );

        return Response.<ApiOffsetPageData<QueryAssistantsResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    @Override
    @RequestMapping(value = "assistant/experts", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询助理的专家列表", recordParams = true)
    public Response<ApiOffsetPageData<QueryExpertsResponseDTO>> queryExperts(
            @RequestParam(name = "page")
            Integer page,
            @RequestParam(name = "size")
            Integer size) {

        Long assistantId = authService.queryValidatedUser().getUserId();
        log.info("助理 {} 开始查询专家列表, page: {}, size: {}", assistantId, page, size);

        OffsetPageResponseData<UserEntity> domainPage = userAssistantService.queryExpertConfig(assistantId, page, size);

        if (domainPage.getData() == null) {
            return Response.<ApiOffsetPageData<QueryExpertsResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(
                            ApiOffsetPageData.<QueryExpertsResponseDTO>builder()
                                    .hasNext(false)
                                    .total(domainPage.getTotal())
                                    .size(0)
                                    .page(domainPage.getPage())
                                    .data(null)
                                    .build()
                    )
                    .build();
        }
        List<QueryExpertsResponseDTO> dtoList = domainPage.getData().stream()
                .map(userEntity -> QueryExpertsResponseDTO.builder()
                        .userId(userEntity.getUserId())
                        .nickName(userEntity.getNickName())
                        .avatar(userEntity.getAvatar())
                        .phoneNumber(userEntity.getPhoneNumber())
                        .gender(userEntity.getGenderEnumVO().getDescription())
                        .build())
                .collect(Collectors.toList());

        ApiOffsetPageData<QueryExpertsResponseDTO> responsePage = new ApiOffsetPageData<>(
                dtoList, domainPage.getHasNext(), domainPage.getTotal(), domainPage.getPage(), domainPage.getSize()
        );

        return Response.<ApiOffsetPageData<QueryExpertsResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    @Override
    @RequestMapping(value = "expert/register-assistant", method = RequestMethod.POST)
    @OperationLog(operationType = "注册", description = "注册助理账号", recordParams = true)
    public Response<Void> registerAssistant(@RequestBody @Valid RegisterAssistantRequestDTO requestDTO) {
        Long expertId = authService.queryValidatedUser().getUserId();
        log.info("专家 {} 开始注册新助理", expertId);


        RegisterAssistantCommandEntity command = RegisterAssistantCommandEntity.builder()
                .expertId(expertId)
                .nickName(requestDTO.getNickName())
                .phoneNumber(requestDTO.getPhoneNumber())
                .password(requestDTO.getPassword())
                .genderEnumVO(GenderEnumVO.fromCode(requestDTO.getGenderCode()))
                .build();

        userExpertService.registerAssistant(command);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "update/send", method = RequestMethod.POST)
    @Idempotent(
            expireTime = 1,
            message = "请勿重复发送验证码"
    )
    @OperationLog(operationType = "发送", description = "发送更新验证码", recordParams = true)
    public Response<Void> sendUpdateShortMessage(@RequestParam String phoneNumber) {
        userManagerService.sendUpdateShortMessage(phoneNumber);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "update/password", method = RequestMethod.POST)
    @OperationLog(operationType = "更新", description = "更新密码", recordParams = true)
    public Response<Void> updatePassword(@RequestBody UpdatePasswordRequestDTO updatePasswordRequestDTO) {
        AuthUserEntity authUserEntity = authService.queryValidatedUser();
        userManagerService.updatePassword(UpdatePasswordCommandEntity.builder()
                .phoneNumber(authUserEntity.getPhoneNumber())
                .password(updatePasswordRequestDTO.getPassword())
                .code(updatePasswordRequestDTO.getCode())
                .build()
        );
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Operation(
            summary = "更新用户昵称",
            description = "用户更新自己的昵称信息，并返回更新后的用户基本信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新成功并返回用户信息"),
                    @ApiResponse(responseCode = "400", description = "参数错误（如昵称格式不正确）"),
                    @ApiResponse(responseCode = "401", description = "用户未登录"),
                    @ApiResponse(responseCode = "500", description = "系统异常")
            }
    )
    @Override
    @RequestMapping(value = "update/nick-name", method = RequestMethod.POST)
    @OperationLog(operationType = "更新", description = "更新用户昵称", recordParams = true)
    public Response<UserUpdateResponseDTO> updateNickName(@RequestParam String nickName) {
        // 获取当前用户信息
        AuthUserEntity validatedUser = authService.queryValidatedUser();

        // 更新用户昵称
        userManagerService.updateNickName(
                UpdateNickNameCommandEntity.builder()
                        .userId(validatedUser.getUserId())
                        .newNickName(nickName)
                        .build()
        );

        // 查询更新后的用户信息
        UserEntity userEntity = userManagerService.getUserById(validatedUser.getUserId());

        // 构建响应DTO
        UserUpdateResponseDTO responseDTO = UserUpdateResponseDTO.builder()
                .userId(userEntity.getUserId())
                .nickName(userEntity.getNickName())
                .avatar(userEntity.getAvatar())
                .gender(userEntity.getGenderEnumVO().getCode())
                .phoneNumber(userEntity.getPhoneNumber())
                .build();

        return Response.<UserUpdateResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responseDTO)
                .build();
    }

    /**
     * 更新用户专业
     * @param specialty
     * @return
     */
    @Override
    @RequestMapping(value = "update/specialty", method = RequestMethod.POST)
    @OperationLog(operationType = "更新", description = "更新用户专业", recordParams = true)
    public Response<UserUpdateResponseDTO> updateSpecialty(@RequestParam String specialty) {
        // 获取当前用户信息
        AuthUserEntity validatedUser = authService.queryValidatedUser();

        // 更新用户专业
        userManagerService.updateSpecialty(
                UpdateSpecialtyCommandEntity.builder()
                        .userId(validatedUser.getUserId())
                        .specialty(specialty)
                        .build()
        );

        // 查询更新后的用户信息
        UserEntity userEntity = userManagerService.getUserById(validatedUser.getUserId());

        // 构建响应DTO
        UserUpdateResponseDTO responseDTO = UserUpdateResponseDTO.builder()
                .userId(userEntity.getUserId())
                .specialty(userEntity.getSpecialty())
                .avatar(userEntity.getAvatar())
                .gender(userEntity.getGenderEnumVO().getCode())
                .phoneNumber(userEntity.getPhoneNumber())
                .build();

        return Response.<UserUpdateResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responseDTO)
                .build();
    }

    @Override
    @RequestMapping(value = "query/is-set-specialty", method = RequestMethod.GET)
    @OperationLog(operationType = "查询用户是否设置专业", description = "查询用户是否设置专业")
    public Response<Boolean> queryIsSetSpecialty() {
        Long userId = authService.queryValidatedUser().getUserId();
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(userManagerService.queryIsSetSpecialty(userId))
                .build();
    }

    /**
     * 更新用户头像接口
     * 核心修改：@RequestAttribute → @RequestParam（正确接收文件上传参数）+ 异常处理 + 接口文档
     */
    @Operation(
            summary = "更新用户头像",
            description = "用户上传头像文件（支持jpg/jpeg/png，≤10MB），自动上传OSS并更新数据库",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新成功并返回用户信息"),
                    @ApiResponse(responseCode = "400", description = "参数错误（如文件为空、格式不支持）"),
                    @ApiResponse(responseCode = "401", description = "用户未登录"),
                    @ApiResponse(responseCode = "500", description = "系统异常（如OSS上传失败、数据库更新失败）")
            }
    )
    @RequestMapping(value = "update/avatar", method = RequestMethod.POST, consumes = "multipart/form-data")
    @OperationLog(operationType = "更新", description = "更新用户头像", recordParams = true)
    // 关键修改1：@RequestAttribute → @RequestParam（接收前端文件上传参数）
    public Response<UserUpdateResponseDTO> updateAvatar(
            @Parameter(
                    description = "头像文件（支持jpg、jpeg、png格式，大小≤10MB）",
                    required = true,
                    content = @Content(mediaType = "multipart/form-data", schema = @Schema(type = "string", format = "binary"))
            )
            @RequestParam("avatar") MultipartFile avatar // name="avatar"需与前端上传的参数名一致
    ) {
        try {
            // 1. 校验文件非空（补充前端参数校验，避免空指针）
            if (avatar == null || avatar.isEmpty()) {
                return Response.<UserUpdateResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("请上传头像文件")
                        .build();
            }

            // 2. 校验用户已认证（避免authService返回null）
            AuthUserEntity validatedUser = authService.queryValidatedUser();
            if (validatedUser == null || validatedUser.getUserId() == null) {
                return Response.<UserUpdateResponseDTO>builder()
                        .code(ResponseCode.AUTHORIZATION_EXCEPTION.getCode())
                        .info("用户未登录，无法更新头像")
                        .build();
            }

            // 3. 调用业务层更新头像
            userManagerService.updateAvatar(
                    UpdateAvatarCommandEntity.builder()
                            .userId(validatedUser.getUserId()) // 已校验非空
                            .avatar(avatar)
                            .build()
            );

            // 4. 获取更新后的用户信息
            UserEntity userEntity = userManagerService.getUserById(validatedUser.getUserId());

            // 5. 构建响应DTO
            UserUpdateResponseDTO responseDTO = UserUpdateResponseDTO.builder()
                    .userId(userEntity.getUserId())
                    .nickName(userEntity.getNickName())
                    .avatar(userEntity.getAvatar())
                    .gender(userEntity.getGenderEnumVO() != null ? userEntity.getGenderEnumVO().getCode() : null)
                    .phoneNumber(userEntity.getPhoneNumber())
                    .build();

            // 6. 成功响应，返回用户信息
            return Response.<UserUpdateResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();

        } catch (AppException e) {
            log.error("用户头像更新业务异常：{}", e.getInfo()); // 日志打印纯文本
            return Response.<UserUpdateResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo()) // 直接返回纯文本，比如 "用户不存在，无法更新头像"
                    .build();
        } catch (Exception e) {
            log.error("用户头像更新系统异常，异常类型: {}", e.getClass().getSimpleName());
            return Response.<UserUpdateResponseDTO>builder()
                    .code(ResponseCode.SYSTEM_ERROR.getCode())
                    .info("系统异常，头像更新失败") // 纯文本提示
                    .build();
        }
    }

    @Override
    @RequestMapping(value = "resource/quota/receive", method = RequestMethod.POST)
    @Idempotent(
            expireTime = 1,
            message = "请勿重复领取"
    )
    @OperationLog(operationType = "领取", description = "领取AI配额", recordParams = true)
    public Response<Void> receiveAiQuota() {
        userResourceService.receiveAiQuota(authService.queryValidatedUser().getUserId());
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "resource/quota/query", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询AI配额", recordParams = true)
    public Response<QueryAIQuotaResponseDTO> queryAiQuota() {
        AIQuotaEntity queryAiQuota = userResourceService.queryAiQuota(authService.queryValidatedUser().getUserId());
        return Response.<QueryAIQuotaResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        QueryAIQuotaResponseDTO.builder()
                                .isReceived(queryAiQuota.getIsReceived())
                                .expireTime(queryAiQuota.getExpireTime())
                                .build()
                )
                .build();
    }


}

