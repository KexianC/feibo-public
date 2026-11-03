package com.hzmwmt.api;

import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiOffsetPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.api.valid.Phone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserApi
 * @Date: 2025/9/3 15:44
 */
public interface IUserApi {
    /**
     * 查询用户AI资源首页 (包含总资源和订阅信息)
     *
     * @return 用户资源聚合信息
     */
    Response<UserResourceResponseDTO> queryUserResourceIndex();

    /**
     * 专家分页查询自己配置的助理列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 助理列表
     */
    Response<ApiOffsetPageData<QueryAssistantsResponseDTO>> queryAssistants(
            @Min(value = 1, message = "页码不能小于1")
            Integer page,
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100")
            Integer size);

    /**
     * 助理分页查询自己配置的专家列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 专家列表
     */
    Response<ApiOffsetPageData<QueryExpertsResponseDTO>> queryExperts(
            @Min(value = 1, message = "页码不能小于1")
            Integer page,
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100")
            Integer size);

    /**
     * 专家注册并绑定一个新助理
     *
     * @param requestDTO 助理注册信息
     * @return 操作结果
     */
    Response<Void> registerAssistant(@Valid RegisterAssistantRequestDTO requestDTO);


    /**
     * 发送更新验证码
     *
     * @param phoneNumber 手机号
     * @return 发送结果
     */
    Response<Void> sendUpdateShortMessage(@Phone String phoneNumber);

    /**
     * 更新密码
     *
     * @return 更新结果
     */
    Response<Void> updatePassword(@Valid UpdatePasswordRequestDTO updatePasswordRequestDTO);

    /**
     * 更新昵称
     *
     * @return 更新结果（包含更新后的用户信息）
     */
    Response<UserUpdateResponseDTO> updateNickName(@NotBlank(message = "昵称不能为空") String nickName);

    /**
     * 更新专业
     * @param specialty
     * @return 更新成功后返回用户信息，包含新专业
     */
    Response<UserUpdateResponseDTO> updateSpecialty(@NotBlank(message = "专业不能为空") String specialty);

    /**
     * 检查是否设置专业
     * @return
     */
    Response<Boolean> queryIsSetSpecialty();

    /**
     * 更新用户头像
     *
     * @param avatar 头像文件
     * @return 更新成功后返回用户信息，包含新头像URL
     */
    Response<UserUpdateResponseDTO> updateAvatar(@NotNull(message = "头像不能为空") MultipartFile avatar);

    /**
     * 接收AI白名单名额
     *
     * @return 接收结果
     */
    Response<Void> receiveAiQuota();

    /**
     * 查询AI白名单名额
     *
     * @return 查询结果
     */
    Response<QueryAIQuotaResponseDTO> queryAiQuota();
}


