package com.hzmwmt.domain.service.function.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TCaseVideo;
import com.hzmwmt.domain.mapper.function.AdminCaseVideoMapper;
import com.hzmwmt.domain.service.function.AdminCaseVideoService;
import com.hzmwmt.types.annotations.OperationLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AdminCaseVideoServiceImpl extends ServiceImpl<AdminCaseVideoMapper, TCaseVideo> implements AdminCaseVideoService {

    // OSS 配置（从 application.yml 注入）
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;
    @Value("${aliyun.oss.bucketName}")
    private String bucketName;
    @Value("${aliyun.oss.baseUrl}")
    private String baseUrl;

    @Override
    public IPage<TCaseVideo> pageCaseVideos(Integer pageNum, Integer pageSize, String title, String scene, String industry) {
        Page<TCaseVideo> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TCaseVideo> wrapper = new QueryWrapper<>();
        if (title != null && !title.isEmpty()) wrapper.like("title", title);
        if (scene != null && !scene.isEmpty()) wrapper.eq("scene", scene);
        if (industry != null && !industry.isEmpty()) wrapper.eq("industry", industry);
        wrapper.orderByAsc("sort").orderByDesc("id");
        return baseMapper.selectPage(page, wrapper);
    }

    /**
     * 上传封面图到 OSS（路径调整为 case-video/cover 更贴合语义）
     */
    @Override
    public String uploadCoverImage(MultipartFile file) {
        return uploadToOss(file, "case-video-cover"); // 原 case-video/index 改为 case-video/cover
    }

    @Override
    public String uploadVideo(MultipartFile file) {
        return uploadToOss(file, "case-video");
    }

    @Override
    @OperationLog(description = "新增案例视频（合并上传）")
    public boolean addCaseVideoWithFiles(String title, String scene, String industry, MultipartFile videoFile, MultipartFile coverImage, Integer sort, Integer isShow) {
        // 上传视频文件
        String videoUrl = uploadVideo(videoFile);
        // 上传封面图片
        String coverUrl = uploadCoverImage(coverImage);

        // 创建并保存案例视频信息
        TCaseVideo caseVideo = new TCaseVideo();
        caseVideo.setTitle(title);
        caseVideo.setScene(scene);
        caseVideo.setIndustry(industry);
        caseVideo.setVideoUrl(videoUrl);
        caseVideo.setCoverUrl(coverUrl);
        caseVideo.setSort(sort);
        caseVideo.setIsShow(isShow);
        caseVideo.setIsDeleted(0);

        return save(caseVideo);
    }

    /**
     * 通用 OSS 上传逻辑
     */
    private String uploadToOss(MultipartFile file, String dirPrefix) {
        if (file.isEmpty()) throw new RuntimeException("文件不能为空");
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) throw new RuntimeException("文件名不能为空");

        // 生成唯一存储路径：dirPrefix/日期/UUID.后缀
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExt = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = dirPrefix + "/" + dateDir + "/" + uuid + fileExt;

        // 上传到 OSS
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.putObject(bucketName, objectName, file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败：" + e.getMessage());
        } finally {
            ossClient.shutdown();
        }

        return baseUrl + objectName;
    }
}
