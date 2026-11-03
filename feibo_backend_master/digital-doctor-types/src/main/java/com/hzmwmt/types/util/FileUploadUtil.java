package com.hzmwmt.types.util;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.helpers.DefaultHandler;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: 文件上传工具类，提供音频时长、格式、采样率检测功能
 * @ClassName FileUploadUtil
 * @Date: 2025/9/13 1:06
 */
public class FileUploadUtil {

    private static final Logger logger = LogManager.getLogger(FileUploadUtil.class);

    // 单文件解析方法
    public static int getAudioDurationWithTika(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw invalidMediaDuration();
        }
        try (InputStream stream = multipartFile.getInputStream()) {
            Parser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(stream, new DefaultHandler(), metadata, context);
            Integer duration = readDurationSeconds(metadata);
            if (duration != null) return duration;
        } catch (Exception e) {
            logger.debug("自动解析媒体时长失败，文件大小: {}，异常类型: {}",
                    multipartFile.getSize(), e.getClass().getSimpleName());
        }

        // Tika 2.9.2 can misdetect ID3-tagged MP3 data as AAC and select EmptyParser.
        // Parse actual frames on a fresh stream; neither filename nor client MIME proves MP3.
        try (InputStream stream = multipartFile.getInputStream()) {
            Metadata metadata = new Metadata();
            new Mp3Parser().parse(stream, new DefaultHandler(), metadata, new ParseContext());
            Integer duration = readDurationSeconds(metadata);
            if (duration != null && hasMp3AudioTrack(metadata)) return duration;
        } catch (Exception e) {
            logger.debug("MP3音轨时长解析失败，文件大小: {}，异常类型: {}",
                    multipartFile.getSize(), e.getClass().getSimpleName());
        }

        // Tika's AudioParser exposes PCM track properties without calculating duration.
        // Count the complete audio frames so valid WAV/AIFF/AU uploads remain usable.
        try (InputStream stream = new BufferedInputStream(multipartFile.getInputStream());
             AudioInputStream audio = AudioSystem.getAudioInputStream(stream)) {
            AudioFormat format = audio.getFormat();
            AudioFormat.Encoding encoding = format.getEncoding();
            if ((AudioFormat.Encoding.PCM_SIGNED.equals(encoding)
                    || AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding)
                    || AudioFormat.Encoding.ALAW.equals(encoding)
                    || AudioFormat.Encoding.ULAW.equals(encoding))
                    && format.getFrameSize() > 0 && format.getFrameSize() <= 8192 && format.getChannels() > 0
                    && Float.isFinite(format.getFrameRate()) && format.getFrameRate() > 0) {
                long bytes = 0;
                byte[] buffer = new byte[8192];
                int count;
                while ((count = audio.read(buffer)) != -1) {
                    if (count == 0) throw new IOException("Audio stream returned no complete frame");
                    bytes += count;
                }
                long frames = bytes / format.getFrameSize();
                boolean complete = bytes % format.getFrameSize() == 0
                        && (audio.getFrameLength() == AudioSystem.NOT_SPECIFIED || audio.getFrameLength() == frames);
                Integer duration = normalizeDurationSeconds(frames / (double) format.getFrameRate());
                if (complete && duration != null) return duration;
            }
        } catch (Exception e) {
            logger.debug("PCM音轨时长解析失败，文件大小: {}，异常类型: {}",
                    multipartFile.getSize(), e.getClass().getSimpleName());
        }
        throw invalidMediaDuration();
    }

    static Integer readDurationSeconds(Metadata metadata) {
        for (String key : new String[]{"xmpDM:duration", "duration", "vorbis:length", "time:duration"}) {
            String value = metadata.get(key);
            if (value == null) continue;
            try {
                Integer duration = normalizeDurationSeconds(Double.parseDouble(value));
                if (duration != null) return duration;
            } catch (NumberFormatException ignored) {
                // Some parsers expose non-numeric values; try the remaining supported keys.
            }
        }
        return null;
    }

    private static Integer normalizeDurationSeconds(double seconds) {
        return Double.isFinite(seconds) && seconds > 0 && seconds <= 86400
                ? (int) Math.max(1L, Math.round(seconds)) : null;
    }

    private static boolean hasMp3AudioTrack(Metadata metadata) {
        if (!"audio/mpeg".equals(metadata.get(Metadata.CONTENT_TYPE))
                || !"MP3".equalsIgnoreCase(metadata.get("xmpDM:audioCompressor"))) return false;
        try {
            int sampleRate = Integer.parseInt(metadata.get("xmpDM:audioSampleRate"));
            int channels = Integer.parseInt(metadata.get("channels"));
            return sampleRate > 0 && (channels == 1 || channels == 2);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static AppException invalidMediaDuration() {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "无法识别有效媒体时长，请检查文件是否完整并重新上传");
    }

    // 多文件解析方法
    public static List<Integer> getAudioDurationsWithTika(List<MultipartFile> multipartFiles) {
        if (multipartFiles == null || multipartFiles.isEmpty()) throw invalidMediaDuration();
        List<Integer> durations = new ArrayList<>();
        for (MultipartFile file : multipartFiles) {
            durations.add(getAudioDurationWithTika(file));
        }
        return durations;
    }

    /**
     * 获取音频文件的格式和采样率信息
     *
     * @param multipartFile 音频文件
     * @param ffmpegPath    ffmpeg/ffprobe的路径
     * @return 包含格式和采样率的AudioInfo对象
     */
    public static AudioInfoUtil.AudioInfo getAudioFormatAndSampleRate(MultipartFile multipartFile, String ffmpegPath) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            logger.error("音频文件为空");
            return null;
        }

        // 创建临时文件
        File tempFile = null;
        try {
            // 生成临时文件
            String originalFilename = multipartFile.getOriginalFilename();
            String suffix = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                    : ".tmp";

            tempFile = File.createTempFile("audio_", suffix);
            multipartFile.transferTo(tempFile);

            // 使用AudioInfoUtil获取音频信息
            return AudioInfoUtil.getAudioInfo(ffmpegPath, tempFile);

        } catch (IOException e) {
            logger.error("创建临时文件或获取音频信息失败: {}", e.getMessage(), e);
            return null;
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                if (tempFile.delete()) {
                    logger.debug("临时文件已删除: {}", tempFile.getAbsolutePath());
                } else {
                    logger.warn("无法删除临时文件: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }
}
