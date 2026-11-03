package com.hzmwmt.api;

import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiCursorPageData;
import com.hzmwmt.api.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalHumanApi
 * @Date: 2025/9/13 14:37
 */
public interface IDigitalHumanApi {
//    /**
//     * 分页查询当前用户的数字人模型列表 (游标分页)
//     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
//     * @param size 每页数量
//     * @return 数字人模型列表
//     */
//    Response<ApiCursorPageData<DigitalModelResponseDTO>> queryDigitalModels(
//            Long lastCursorId,
//            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);


    /**
     * 分页查询当前用户的个人背景音乐列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 个人背景音乐列表
     */
    Response<ApiCursorPageData<DigitalAudioResponseDTO>> queryUserMusic(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询当前用户的个人录音列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 个人录音列表
     */
    Response<ApiCursorPageData<DigitalAudioResponseDTO>> queryUserRecordings(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询系统预设的背景音乐列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 系统背景音乐列表
     */
    Response<ApiCursorPageData<DigitalAudioResponseDTO>> querySystemMusic(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询当前用户的个人背景图片列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 个人背景图片列表
     */
    Response<ApiCursorPageData<DigitalImageResponseDTO>> queryUserBackgroundImages(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询当前用户的个人插图列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 个人插图列表
     */
    Response<ApiCursorPageData<DigitalImageResponseDTO>> queryUserIllustrationImages(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询系统预设的背景图片列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 系统背景图片列表
     */
    Response<ApiCursorPageData<DigitalImageResponseDTO>> querySystemBackgroundImages(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询系统预设的插图列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 系统插图列表
     */
    Response<ApiCursorPageData<DigitalImageResponseDTO>> querySystemIllustrationImages(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询当前用户的个人视频列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 个人视频列表
     */
    Response<ApiCursorPageData<DigitalVideoResponseDTO>> queryUserVideos(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 分页查询系统预设的视频列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 系统视频列表
     */
    Response<ApiCursorPageData<DigitalVideoResponseDTO>> querySystemVideos(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 上传单个文件
     *
     * @param file 包含文件和上传类型的数据对象
     * @return 文件的访问URL
     */
    Response<String> uploadSingleFile(@RequestParam("userId") Long userId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam("uploadType") Integer uploadType);

    /**
     * 上传多个文件
     *
     * @param requestDTO 包含文件列表和上传类型的数据对象
     * @return 文件访问URL的列表
     */
    Response<List<String>> uploadMultipleFiles(@Valid MultipleFileUploadRequestDTO requestDTO);


    /**
     * 分页查询当前用户的个人模板列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 个人模板列表
     */
    Response<ApiCursorPageData<QueryDigitalTemplateResponseDTO>> queryUserTemplates(Long lastCursorId, Integer size);

    /**
     * 分页查询系统预设的模板列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 系统模板列表
     */
    Response<ApiCursorPageData<QueryDigitalTemplateResponseDTO>> querySystemTemplates(Long lastCursorId, Integer size);

    /**
     * 为当前用户创建新的数字模板
     *
     * @param requestDTO 包含模板标题和内容的请求体
     * @return 操作结果
     */
    Response<Void> createUserTemplate(@Valid CreateDigitalTemplateRequestDTO requestDTO);

    /**
     * 删除当前用户的指定数字模板
     *
     * @param digitalTemplateId 要删除的模板ID
     * @return 操作结果
     */
    Response<Void> deleteTemplate(@NotNull(message = "模板ID不能为空") Long digitalTemplateId);

    /**
     * 更新当前用户的指定数字模板标题
     *
     * @param requestDTO 包含模板ID和新标题的请求体
     * @return 操作结果
     */
    Response<Void> updateTemplateTitle(@Valid UpdateDigitalTemplateTitleRequestDTO requestDTO);

    /**
     * 创建当前用户的数字人作品
     *
     * @param requestDTO 创建作品请求体
     * @return 创建结果
     */
    Response<String> videoComposition(@Valid DigitalVideoCompositionRequestDTO requestDTO);

    /**
     * 删除当前用户的指定数字人作品
     *
     * @param digitalWorkId 要删除的作品ID
     * @return 操作结果
     */
    Response<Void> deleteDigitalWork(@NotNull(message = "作品ID不能为空") Long digitalWorkId);

    /**
     * 分页查询数字人作品列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 数字人作品列表
     */
    Response<ApiCursorPageData<QueryDigitalWorksResponseDTO>> queryDigitalWorks(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 更新当前用户的指定数字人作品标题
     *
     * @param requestDTO 包含作品ID和新标题的请求体
     * @return 操作结果
     */
    Response<Void> updateDigitalWorkTitle(@Valid UpdateDigitalWorkTitleRequestDTO requestDTO);

    // --- 数字人声音组件接口 ---

    /**
     * 分页查询声音首页列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 声音聚合信息列表
     */
    Response<ApiCursorPageData<QueryDigitalVoicesResponseDTO>> queryVoices(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 高级筛选查询声音列表 (游标分页)
     *
     * @param requestDTO 包含所有筛选条件的请求对象
     * @return 符合条件的声音聚合信息列表
     */
    Response<ApiCursorPageData<QueryDigitalVoicesResponseDTO>> advancedQueryVoices(@Valid AdvancedDigitalVoiceQueryRequestDTO requestDTO);

    /**
     * 分页查询当前用户收藏的声音列表 (游标分页)
     *
     * @param lastCursorId 上一页最后一条记录的ID，首页查询为null
     * @param size         每页数量
     * @return 用户收藏的声音列表
     */
    Response<ApiCursorPageData<QueryCollectedDigitalVoicesResponseDTO>> queryCollectedVoices(
            Long lastCursorId,
            @Min(value = 1, message = "每页数量不能小于1") @Max(value = 50, message = "每页数量不能超过50") Integer size);

    /**
     * 更新声音的收藏状态
     *
     * @param requestDTO 包含声音ID和收藏状态的请求体
     * @return 操作结果
     */
    Response<Void> updateVoiceFavoriteStatus(@Valid UpdateDigitalVoiceFavoriteStatusRequestDTO requestDTO);

    /**
     * 查询指定类型的声音分类列表
     *
     * @param categoryType 分类类型代码 (0:年龄, 1:领域, 2:风格, 3:语言)
     * @return 对应类型的分类列表
     */
    Response<List<DigitalVoiceCategoryResponseDTO>> queryVoiceCategories(
            @NotNull(message = "分类类型不能为空") Integer categoryType);
}

