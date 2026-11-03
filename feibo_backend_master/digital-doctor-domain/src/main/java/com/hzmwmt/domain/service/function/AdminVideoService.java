package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.common.VideoUploadResult;
import com.hzmwmt.domain.entity.function.TVideo;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.multipart.MultipartFile;

@Hidden // 隐藏 Service，不暴露到 Swagger 文档
public interface AdminVideoService extends IService<TVideo> {

    /**
     * 分页+标题模糊查询
     */
    IPage<TVideo> pageWithLike(Integer pageNum, Integer pageSize, String title);

    /**
     * 上传封面到 OSS
     */
    String uploadCoverToOss(MultipartFile file);

    /**
     * 上传视频到 OSS
     */
    // 上传视频到OSS（返回URL和时长）
    VideoUploadResult uploadVideoToOss(MultipartFile file); // 修改：返回包含时长的结果

    /**
     * 直接上传文件并保存视频信息
     *
     * @param video     视频基本信息
     * @param file      视频文件
     * @param coverFile 封面文件
     * @return 是否保存成功
     */
    boolean saveWithFileUpload(TVideo video, MultipartFile file, MultipartFile coverFile);
}
