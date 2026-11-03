package com.hzmwmt.types.common;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class Constants {
    public final static String DEFAULT_AVATAR = "https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/avatar/default.jpg";
    public final static String DEFAULT_NICKNAME = "木吾木同";
    public final static String SPLIT = ",";
    public final static int ORDER_VALIDITY_MINUTES = 15;
    public final static String T_AVATAR_DURATION = "T_AVATAR_DURATION";
    //永久结束时间
    public static final LocalDateTime PERMANENT_END_TIME =
            LocalDateTime.of(9999, 12, 31, 23, 59, 59);
    public static final Long DEFAULT_ROLE_ID = 1964218007207526401L;

    public static class RedisConstants {
        public static final String KEY_CATEGORY_VOICE_STYLE_PREFIX = "category:voice:style:";
        public static final String KEY_CATEGORY_VOICE_AGE_PREFIX = "category:voice:age:";
        public static final String KEY_CATEGORY_VOICE_DOMAIN_PREFIX = "category:voice:domain:";
        public static final String KEY_CATEGORY_VOICE_LANGUAGE_PREFIX = "category:voice:language:";
        public static final String KEY_VOICE_COLLECT_PREFIX = "voice:collect:";
        public static final String KEY_REFRESH_TOKEN_PREFIX = "token:refresh";
        public static final String KEY_REFRESH_FAMILY_PREFIX = "token:refresh:family:";
        public static final String KEY_AUTH_CODE_PREFIX = "auth:code:";
        public static final String KEY_UPDATE_CODE_PREFIX = "update:code:";
        public static final String KEY_AI_WRITE_LIST_PREFIX = "ai:write:list:";
        public static final String KEY_IDEMPOTENT_TOKEN_PREFIX = "token:idempotent:";
        public static final String KEY_ORDER_LOCK = "lock:order:";
        public static final String KEY_PAYMENT_URL = "payment:url:";
    }

    /**
     * OSS 相关常量
     */
    public static final class OssConstants {

        // === 文件夹路径 ===
        public static final String UPLOAD_IMAGE_FOLDER = "images/";
        public static final String UPLOAD_AUDIO_FOLDER = "audio/";
        public static final String UPLOAD_VIDEO_FOLDER = "videos/";

        // === 图片上传策略常量 ===
        public static final List<String> UPLOAD_IMAGE_ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif");
        public static final long UPLOAD_IMAGE_MAX_SIZE = 10 * 1024 * 1024; // 10MB
        public static final String UPLOAD_IMAGE_MAX_SIZE_STRING = "10MB";
        public static final String UPLOAD_IMAGE_TYPE_STRING = "JPEG, PNG, GIF";

        // === 音频上传策略常量 ===
        public static final List<String> UPLOAD_AUDIO_ALLOWED_CONTENT_TYPES = Arrays.asList("audio/mpeg", "audio/wav", "audio/wave", "audio/x-wav", "audio/aac", "audio/mp4a-latm", "audio/mp4", "audio/x-m4a", "audio/m4a");
        public static final long UPLOAD_AUDIO_MAX_SIZE = 50 * 1024 * 1024; // 50MB
        public static final String UPLOAD_AUDIO_MAX_SIZE_STRING = "50MB";
        public static final String UPLOAD_AUDIO_TYPE_STRING = "MP3, WAV, AAC, M4A";

        // === 视频上传策略常量 ===
        public static final List<String> UPLOAD_VIDEO_ALLOWED_CONTENT_TYPES = Arrays.asList("video/mp4", "video/quicktime", "video/x-msvideo");
        public static final long UPLOAD_VIDEO_MAX_SIZE = 500 * 1024 * 1024; // 500MB
        public static final String UPLOAD_VIDEO_MAX_SIZE_STRING = "500MB";
        public static final String UPLOAD_VIDEO_TYPE_STRING = "MP4, MOV, AVI";
        //上传4个
        public static final int UPLOAD_BATCH_MAX_TOTAL_SIZE = 5;
    }

    public static class AIChatConstants {
        public static final int TITLE_MAX_LENGTH = 100;
        public static final int CONTENT_MAX_LENGTH = 600;
        public static final String MODEL_SUFFIX = "-model";
    }

    public static class SysMessageConstants {
        public static final String TREAD_TITLE = "交易通知";
        public static final String APPROVAL_TITLE = "审批通知";
    }

    public static class AuthConstants {
        public static final String[] PUBLIC_ENDPOINTS = {
                "/api/*/auth/login/verification-code",
                "/api/*/auth/login/password",
                "/api/*/auth/login/send",
                "/api/*/trade/payment_success_handling",
                // 新增：视频生成回调接口（不被拦截）
                "/api/*/digital-human/work/video-callback",
                // 新增：Swagger 相关路径（解决之前的 401 问题）
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                // 新增：静态资源（如 favicon.ico）
                "/favicon.ico",
        };
    }
}

