package com.hzmwmt.types.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 音频信息工具类，用于获取音频的真实格式和采样率
 */
public class AudioInfoUtil {

    private static final Logger logger = LogManager.getLogger(AudioInfoUtil.class);

    /**
     * 使用ffprobe获取音频的真实格式和采样率
     *
     * @param ffmpegPath ffmpeg/ffprobe的路径
     * @param audioFile  音频文件
     * @return 包含格式和采样率的AudioInfo对象
     */
    public static AudioInfo getAudioInfo(String ffmpegPath, File audioFile) {
        if (!audioFile.exists() || !audioFile.isFile()) {
            logger.error("音频文件不存在或不是文件: {}", audioFile.getAbsolutePath());
            return null;
        }

        try {
            // 构建ffprobe命令
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath, // 假设ffmpegPath同时指向ffmpeg和ffprobe，或系统路径中有ffprobe
                    "-v", "error",
                    "-show_entries", "format=format_name:stream=codec_name,sample_rate,channels",
                    "-of", "default=noprint_wrappers=1",
                    audioFile.getAbsolutePath()
            );

            logger.info("执行ffprobe命令获取音频信息: {} -v error -show_entries format=format_name:stream=codec_name,sample_rate,channels -of default=noprint_wrappers=1 {}",
                    ffmpegPath, audioFile.getAbsolutePath());

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();

            // 读取标准输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取错误输出
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                logger.error("ffprobe执行失败，退出码: {}, 错误输出: {}", exitCode, errorOutput.toString());
                return null;
            }

            String ffprobeOutput = output.toString();
            logger.info("ffprobe输出: {}", ffprobeOutput);

            // 解析输出，提取格式和采样率
            return parseAudioInfo(ffprobeOutput);

        } catch (IOException | InterruptedException e) {
            logger.error("获取音频信息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析ffprobe的输出，提取音频格式和采样率
     *
     * @param output ffprobe的输出
     * @return 包含格式和采样率的AudioInfo对象
     */
    private static AudioInfo parseAudioInfo(String output) {
        String format = null;
        Integer sampleRate = null;
        String codecName = null;
        Integer channels = null;

        // 解析格式
        Pattern formatPattern = Pattern.compile("format_name=(.+)");
        Matcher formatMatcher = formatPattern.matcher(output);
        if (formatMatcher.find()) {
            format = formatMatcher.group(1).split(",")[0]; // 取第一个格式
        }

        // 解析音频流信息
        Pattern codecPattern = Pattern.compile("codec_name=(.+)");
        Matcher codecMatcher = codecPattern.matcher(output);
        if (codecMatcher.find()) {
            codecName = codecMatcher.group(1);
        }

        Pattern sampleRatePattern = Pattern.compile("sample_rate=(\\d+)");
        Matcher sampleRateMatcher = sampleRatePattern.matcher(output);
        if (sampleRateMatcher.find()) {
            try {
                sampleRate = Integer.parseInt(sampleRateMatcher.group(1));
            } catch (NumberFormatException e) {
                logger.error("解析采样率失败: {}", sampleRateMatcher.group(1));
            }
        }

        Pattern channelsPattern = Pattern.compile("channels=(\\d+)");
        Matcher channelsMatcher = channelsPattern.matcher(output);
        if (channelsMatcher.find()) {
            try {
                channels = Integer.parseInt(channelsMatcher.group(1));
            } catch (NumberFormatException e) {
                logger.error("解析声道数失败: {}", channelsMatcher.group(1));
            }
        }

        // 如果没有找到格式，尝试从编解码器推断
        if (format == null && codecName != null) {
            format = inferFormatFromCodec(codecName);
        }

        // 特殊处理m4a格式
        if (format != null && format.equals("mp4") && codecName != null && (codecName.equals("mp4a") || codecName.equals("m4a"))) {
            format = "m4a";
        }

        logger.info("解析音频信息 - 格式: {}, 编解码器: {}, 采样率: {}, 声道数: {}", format, codecName, sampleRate, channels);
        return new AudioInfo(format, sampleRate, codecName, channels);
    }

    /**
     * 从编解码器推断音频格式
     *
     * @param codecName 编解码器名称
     * @return 推断的格式
     */
    private static String inferFormatFromCodec(String codecName) {
        switch (codecName.toLowerCase()) {
            case "mp3":
                return "mp3";
            case "pcm_s16le":
            case "pcm_s24le":
            case "pcm_s32le":
            case "pcm_u8":
                return "wav";
            case "aac":
            case "mp4a":
            case "m4a":
                return "mp4";
            case "flac":
                return "flac";
            case "vorbis":
                return "ogg";
            default:
                return codecName.toLowerCase();
        }
    }

    /**
     * 音频信息类
     */
    public static class AudioInfo {
        private String format;
        private Integer sampleRate;
        private String codecName;
        private Integer channels;

        public AudioInfo(String format, Integer sampleRate, String codecName, Integer channels) {
            this.format = format;
            this.sampleRate = sampleRate;
            this.codecName = codecName;
            this.channels = channels;
        }

        // Getters
        public String getFormat() {
            return format;
        }

        public Integer getSampleRate() {
            return sampleRate;
        }

        public String getCodecName() {
            return codecName;
        }

        public Integer getChannels() {
            return channels;
        }

        @Override
        public String toString() {
            return "AudioInfo{" +
                    "format='" + format + '\'' +
                    ", sampleRate=" + sampleRate +
                    ", codecName='" + codecName + '\'' +
                    ", channels=" + channels +
                    '}';
        }
    }
}