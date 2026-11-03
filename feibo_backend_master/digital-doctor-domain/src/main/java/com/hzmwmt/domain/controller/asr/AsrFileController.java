package com.hzmwmt.domain.controller.asr;

import com.hzmwmt.domain.service.asr.impl.DashScopeFileASRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/asr")
@Profile("asr-prototype")
public class AsrFileController {

    private static final Logger logger = LoggerFactory.getLogger(AsrFileController.class);

    @Autowired
    private DashScopeFileASRService fileASRService;

    // 移除了 Tika，因为 DashScope 服务端会自动检测格式，不需要本地检测

    @PostMapping("/upload")
    public String uploadAudio(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "文件为空";
        }

        // 校验文件格式
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.matches(".*\\.(?i:mp3|wav|flac|aac|m4a)$")) {
            return "仅支持mp3、wav、flac、aac、m4a格式";
        }

        try {
            logger.info("接收到文件: {}, 大小: {}", file.getOriginalFilename(), file.getSize());

            // 1. 【关键步骤】将 MultipartFile 上传到 OSS/对象存储，并获取公网 URL
            // DashScope 无法读取你本地的临时文件，必须通过 URL 访问
            String fileUrl = uploadToStorage(file);

            if (fileUrl == null || !fileUrl.startsWith("http")) {
                return "文件上传存储服务失败，无法获取有效的 URL";
            }

            logger.info("文件已上传至: {}", fileUrl);

            // 2. 调用 Service 进行转写 (传入 URL)
            return fileASRService.transcribe(fileUrl);

        } catch (Exception e) {
            logger.error("处理失败", e);
            return "语音识别处理失败，请稍后重试";
        }
    }

    /**
     * TODO: 你需要实现这个方法，将文件上传到阿里云 OSS 或其他存储服务
     *
     * @param file 前端上传的文件
     * @return 文件的公网访问链接 (http/https 开头)
     */
    private String uploadToStorage(MultipartFile file) throws Exception {
        // ============================================================
        // 示例伪代码：请接入你的 OSS 工具类
        // ============================================================

        // String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        // String url = ossUtil.upload(file.getInputStream(), fileName);
        // return url;

        // 临时测试提示：
        throw new UnsupportedOperationException(
                "请在 AsrFileController.uploadToStorage 方法中实现 OSS 上传逻辑！" +
                        "阿里云 DashScope 必须通过公网 URL 才能读取音频文件。"
        );
    }
}
