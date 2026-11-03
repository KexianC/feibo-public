package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TCaseVideo;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.multipart.MultipartFile;

@Hidden
public interface AdminCaseVideoService extends IService<TCaseVideo> {

    /**
     * 分页查询案例视频（支持多条件筛选）
     */
    IPage<TCaseVideo> pageCaseVideos(Integer pageNum, Integer pageSize, String title, String scene, String industry);

    /**
     * 上传封面图到 OSS（原上传索引图方法改名）
     *
     * @deprecated 已合并到 addCaseVideoWithFiles 方法中
     */
    @Deprecated
    String uploadCoverImage(MultipartFile file); // 原 uploadIndexImage 改为 uploadCoverImage

    /**
     * 上传视频到 OSS
     *
     * @deprecated 已合并到 addCaseVideoWithFiles 方法中
     */
    @Deprecated
    String uploadVideo(MultipartFile file);

    /**
     * 新增案例视频并上传文件
     * 同时处理视频文件和封面图的上传，并保存案例视频信息
     */
    boolean addCaseVideoWithFiles(String title, String scene, String industry, MultipartFile videoFile, MultipartFile coverImage, Integer sort, Integer isShow);
}
