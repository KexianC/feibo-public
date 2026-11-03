package com.hzmwmt.domain.service.function.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.common.VideoUploadResult;
import com.hzmwmt.domain.entity.function.TVideo;
import com.hzmwmt.domain.mapper.function.AdminVideoMapper;
import com.hzmwmt.domain.service.function.AdminVideoService;
import com.hzmwmt.types.annotations.OperationLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AdminVideoServiceImpl extends ServiceImpl<AdminVideoMapper, TVideo> implements AdminVideoService {

    private static final Logger logger = LogManager.getLogger(AdminVideoServiceImpl.class);

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

    // 配置ffmpeg路径，默认为系统PATH中的ffmpeg
    @Value("${video.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    // FFmpeg可用性检查标志，用于降级处理
    private volatile boolean isFFmpegAvailable = true;

    /**
     * 分页+标题模糊查询（自动过滤已删除数据）
     */
    @Override
    public IPage<TVideo> pageWithLike(Integer pageNum, Integer pageSize, String title) {
        Page<TVideo> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TVideo> wrapper = new QueryWrapper<>();
        if (title != null && !title.isEmpty()) {
            wrapper.like("title", title);
        }
        return baseMapper.selectPage(page, wrapper);
    }

    /**
     * 上传封面到 OSS
     */
    @Override
    public String uploadCoverToOss(MultipartFile file) {
        return uploadToOss(file, "video/cover");
    }

    /**
     * 上传视频到OSS（并解析时长）
     */
    @Override
    public VideoUploadResult uploadVideoToOss(MultipartFile file) {
        // 1. 上传视频到OSS，获取URL
        String videoUrl = uploadToOss(file, "video/file");

        // 2. 解析视频时长（格式：HH:mm:ss）
        String duration = parseVideoDuration(file);

        // 3. 返回URL和时长
        VideoUploadResult result = new VideoUploadResult();
        result.setVideoUrl(videoUrl);

        // 计算秒数 - 这里采用更简单直接的方式，避免复杂的解析逻辑
        Integer durationSeconds = 0;
        if (duration != null && !duration.isEmpty() && !"00:00:00".equals(duration)) {
            try {
                String[] parts = duration.split(":");
                if (parts.length == 3) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    int seconds = Integer.parseInt(parts[2]);
                    durationSeconds = hours * 3600 + minutes * 60 + seconds;
                } else if (parts.length == 2) {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = Integer.parseInt(parts[1]);
                    durationSeconds = minutes * 60 + seconds;
                }
            } catch (Exception e) {
                logger.warn("解析时长字符串为秒数时出错，异常类型: {}",
                        e.getClass().getSimpleName());
            }
        }
        result.setDurationSeconds(durationSeconds);
        return result;
    }

    /**
     * 通用 OSS 上传逻辑
     */
    private String uploadToOss(MultipartFile file, String dirPrefix) {
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new RuntimeException("文件名不能为空");
        }

        // 生成唯一文件名：日期 + UUID + 后缀
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExt = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = dirPrefix + "/" + dateDir + "/" + uuid + fileExt;

        // 上传到 OSS
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.putObject(bucketName, objectName, file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("上传失败：" + e.getMessage());
        } finally {
            ossClient.shutdown();
        }

        return baseUrl + objectName;
    }

    /**
     * 解析视频时长（秒）- 依赖FFmpeg工具
     * 需先在服务器安装FFmpeg：https://ffmpeg.org/download.html
     */
    /**
     * 将秒数格式化为时间字符串（HH:mm:ss）
     *
     * @param seconds 视频时长（秒）
     * @return 格式化后的时间字符串，例如：00:02:30
     */
    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "00:00:00";
        }

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainingSeconds = seconds % 60;

        // 使用String.format确保格式化为两位数
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    private String parseVideoDuration(MultipartFile file) {
        logger.info("开始解析视频时长");
        logger.info("文件大小: {}字节", file.getSize());

        // 检查ffmpeg路径配置
        if (ffmpegPath == null || ffmpegPath.trim().isEmpty()) {
            logger.error("FFmpeg路径未配置，无法解析视频时长");
            return "00:00:00"; // 使用默认时长
        }

        // 如果之前检测到FFmpeg不可用，直接返回默认值
        if (!isFFmpegAvailable) {
            logger.warn("FFmpeg之前检测为不可用，跳过视频时长解析");
            return "00:00:00";
        }

        File tempFile = null;
        try {
            // 临时保存文件到本地（FFmpeg需要本地文件路径）
            String fileExtension = ".mp4"; // 默认扩展名
            if (file.getOriginalFilename() != null && file.getOriginalFilename().lastIndexOf('.') > 0) {
                fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'));
            }
            tempFile = File.createTempFile("temp-video", fileExtension);
            file.transferTo(tempFile);
            String tempFilePath = tempFile.getAbsolutePath();
            logger.info("临时文件已创建，文件大小: {}字节", tempFile.length());

            // 方法1: 尝试使用ffprobe方式获取时长（如果ffmpeg路径实际上是ffprobe）
            String duration = tryGetDurationWithProbe(ffmpegPath, tempFilePath);
            if (duration != null) {
                return duration;
            }

            // 方法2: 尝试标准的FFmpeg -i命令获取Duration信息
            duration = tryGetDurationWithFFmpegInfo(ffmpegPath, tempFilePath);
            if (duration != null) {
                return duration;
            }

            // 方法3: 尝试使用ffmpeg转码到null输出的方式（更通用）
            duration = tryGetDurationWithTranscode(ffmpegPath, tempFilePath);
            if (duration != null) {
                return duration;
            }

            // 所有方法都失败，返回默认值
            logger.warn("所有获取视频时长的方法都失败，返回默认时长00:00:00");
            return "00:00:00";
        } catch (IOException e) {
            logger.error("处理视频文件时出错，异常类型: {}",
                    e.getClass().getSimpleName());
            return "00:00:00";
        } catch (Exception e) {
            logger.error("解析视频时长时发生未知错误，异常类型: {}",
                    e.getClass().getSimpleName());
            return "00:00:00";
        } finally {
            // 确保临时文件被删除
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                logger.info("临时文件删除状态: {}", deleted ? "成功" : "失败");
            }
        }
    }

    /**
     * 使用ffprobe方式获取视频时长
     */
    private String tryGetDurationWithProbe(String ffmpegPath, String filePath) {
        try {
            // 尝试使用ffprobe参数
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    filePath
            );

            logger.info("尝试使用ffprobe方式获取时长");

            // 分别读取标准输出和错误输出
            Process process = processBuilder.start();

            // 读取标准输出
            StringBuilder stdoutOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutOutput.append(line.trim());
                }
            }
            logger.debug("ffprobe标准输出读取完成，字符数: {}", stdoutOutput.length());

            // 读取错误输出（因为ffmpeg有时会将信息输出到错误流）
            StringBuilder stderrOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                }
            }
            logger.debug("ffprobe错误输出读取完成，字符数: {}", stderrOutput.length());

            int exitCode = process.waitFor();

            // 对于只读操作，ffmpeg可能返回非零退出码但实际成功获取了信息
            // 只在真正的错误（如进程无法启动）时才标记FFmpeg不可用
            if (exitCode != 0) {
                logger.warn("FFmpeg执行返回非零退出码: {}", exitCode);
                // 不在这里设置isFFmpegAvailable = false，因为这可能是正常行为
            }

            // 优先检查标准输出
            String durationStr = stdoutOutput.toString().trim();
            if (!durationStr.isEmpty() && durationStr.matches("\\d+(\\.\\d+)?")) {
                return formatDurationFromSeconds(durationStr);
            }

            // 如果标准输出为空，检查错误输出中是否有Duration信息
            String errorOutput = stderrOutput.toString();
            return extractDurationFromOutput(errorOutput);
        } catch (Exception e) {
            logger.warn("ffprobe方式获取时长失败，异常类型: {}",
                    e.getClass().getSimpleName());
            // 标记FFmpeg不可用
            // 只在真正无法执行命令时才标记不可用
            // 但不要立即设置为false，给后续重试机会
            logger.info("标记FFmpeg可能需要重试");
            return null;
        }
    }

    /**
     * 使用FFmpeg -i命令获取Duration信息
     */
    private String tryGetDurationWithFFmpegInfo(String ffmpegPath, String filePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", filePath
            );

            logger.info("尝试使用FFmpeg -i方式获取时长");

            Process process = processBuilder.start();

            // 读取错误输出（因为FFmpeg -i的信息会输出到错误流）
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }
            logger.debug("FFmpeg -i错误输出读取完成，字符数: {}", errorOutput.length());

            int exitCode = process.waitFor();

            // 对于只读操作，ffmpeg可能返回非零退出码但实际成功获取了信息
            // 只在真正的错误（如进程无法启动）时才标记FFmpeg不可用
            if (exitCode != 0) {
                logger.warn("FFmpeg执行返回非零退出码: {}", exitCode);
                // 不在这里设置isFFmpegAvailable = false，因为这可能是正常行为
            }

            return extractDurationFromOutput(errorOutput.toString());
        } catch (Exception e) {
            logger.warn("FFmpeg -i方式获取时长失败，异常类型: {}",
                    e.getClass().getSimpleName());
            // 标记FFmpeg不可用
            // 只在真正无法执行命令时才标记不可用
            // 但不要立即设置为false，给后续重试机会
            logger.info("标记FFmpeg可能需要重试");
            return null;
        }
    }

    /**
     * 使用ffmpeg转码到null输出的方式获取时长
     */
    private String tryGetDurationWithTranscode(String ffmpegPath, String filePath) {
        try {
            // 检查操作系统类型
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String nullOutput = isWindows ? "NUL" : "/dev/null";

            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", filePath,
                    "-f", "null",
                    nullOutput
            );

            logger.info("尝试使用转码方式获取时长");

            Process process = processBuilder.start();

            // 读取错误输出
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");

                    // 如果找到Duration行，可以提前返回
                    if (line.toLowerCase().contains("duration:")) {
                        logger.info("转码输出检测到Duration标记");
                        String duration = extractDurationFromLine(line);
                        if (duration != null) {
                            return duration;
                        }
                    }
                }
            }

            int exitCode = process.waitFor();

            // 对于只读操作，ffmpeg可能返回非零退出码但实际成功获取了信息
            // 只在真正的错误（如进程无法启动）时才标记FFmpeg不可用
            if (exitCode != 0) {
                logger.warn("FFmpeg执行返回非零退出码: {}", exitCode);
                // 不在这里设置isFFmpegAvailable = false，因为这可能是正常行为
            }

            return extractDurationFromOutput(errorOutput.toString());
        } catch (Exception e) {
            logger.warn("转码方式获取时长失败，异常类型: {}",
                    e.getClass().getSimpleName());
            // 标记FFmpeg不可用
            // 只在真正无法执行命令时才标记不可用
            // 但不要立即设置为false，给后续重试机会
            logger.info("标记FFmpeg可能需要重试");
            return null;
        }
    }

    /**
     * 从输出字符串中提取时长
     */
    private String extractDurationFromOutput(String output) {
        // 查找包含Duration的行
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("duration:")) {
                String duration = extractDurationFromLine(line);
                if (duration != null) {
                    return duration;
                }
            }
        }
        return null;
    }

    /**
     * 从单行文本中提取时长
     */
    private String extractDurationFromLine(String line) {
        try {
            // 使用正则表达式匹配时长格式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "duration:\s*([\\d:.]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                String durationStr = matcher.group(1).trim();
                logger.info("提取到时长字符串: {}", durationStr);

                // 删除毫秒部分
                if (durationStr.contains(".")) {
                    durationStr = durationStr.split("\\.")[0];
                }

                // 验证格式 HH:mm:ss 或 mm:ss
                if (durationStr.matches("\\d{2}:\\d{2}:\\d{2}") || durationStr.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                    return durationStr;
                }
                // 处理 mm:ss 格式
                else if (durationStr.matches("\\d{1,2}:\\d{2}")) {
                    return "00:" + durationStr; // 补上前导00小时
                }
            }
        } catch (Exception e) {
            logger.error("从行中提取时长失败，输入长度: {}，异常类型: {}",
                    line == null ? 0 : line.length(), e.getClass().getSimpleName());
        }
        return null;
    }

    /**
     * 将秒数字符串格式化为HH:mm:ss格式
     */
    private String formatDurationFromSeconds(String secondsStr) {
        try {
            double durationSeconds = Double.parseDouble(secondsStr);
            logger.info("解析得到秒数: {}", durationSeconds);

            // 转换为整数秒
            int seconds = (int) Math.round(durationSeconds);
            logger.info("四舍五入后秒数: {}", seconds);

            // 转换为HH:mm:ss格式
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            int remainingSeconds = seconds % 60;

            String formatted = String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
            logger.info("格式化后的时长: {}", formatted);
            return formatted;
        } catch (NumberFormatException e) {
            logger.error("无法将时长转换为数字: {}", secondsStr);
            return null;
        }
    }

    /**
     * 直接上传文件并保存视频信息
     */
    @Override
    @OperationLog(operationType = "添加", description = "新增视频（直接上传文件）")
    public boolean saveWithFileUpload(TVideo video, MultipartFile file, MultipartFile coverFile) {
        // 1. 上传视频文件并获取URL和时长
        VideoUploadResult videoResult = uploadVideoToOss(file);
        video.setVideoUrl(videoResult.getVideoUrl());

        // 使用videoResult中的durationSeconds格式化时间字符串，避免重复解析视频文件
        String duration = formatDuration(videoResult.getDurationSeconds());
        video.setDuration(duration);
        logger.info("设置视频时长: {} ({}秒)", duration, videoResult.getDurationSeconds());

        // 2. 上传封面文件并获取URL
        String coverUrl = uploadCoverToOss(coverFile);
        video.setCoverUrl(coverUrl);

        // 3. 设置未删除状态（如果需要）
        video.setIsDeleted(0); // 0表示未删除

        // 4. 保存到数据库
        boolean saved = save(video);
        logger.info("视频保存完成，saved: {}，videoId: {}，durationSeconds: {}",
                saved, saved ? video.getId() : null, videoResult.getDurationSeconds());
        return saved;
    }
}
