package com.hzmwmt.domain.service.asr.impl;

import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.utils.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class DashScopeFileASRService {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeFileASRService.class);

    @Value("${aliyun.dashscope.apiKey}")
    private String apiKey;

    // 如果 ffmpeg 仅用于获取信息传给 ASR，可以移除它，因为 fun-asr 模型支持自动检测格式

    /**
     * 提交文件转写任务
     * 注意：fileUrl 必须是公网可访问的 URL (http/https)，不能是本地文件路径！
     * 如果文件在本地，请先上传到 OSS。
     */
    public String transcribe(String fileUrl) {
        try {
            // 1. 设置 API 接入点 (建议加上，防止环境差异)
            Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";

            // 2. 创建Transcription实例
            Transcription transcription = new Transcription();

            // 3. 创建转写请求参数
            // 相比旧代码，去掉了 ffmpeg 检测，直接依赖云端自动检测
            TranscriptionParam param = TranscriptionParam.builder()
                    .apiKey(apiKey)
                    .model("paraformer-v1")
                    // fileUrls 接收 List，这里使用 Collections.singletonList 包装单个 URL
                    .fileUrls(Collections.singletonList(fileUrl))
                    .build();

            logger.info("开始提交转写任务，URL: {}", fileUrl);

            // 4. 提交转写请求 (异步)
            TranscriptionResult result = transcription.asyncCall(param);
            logger.info("任务已提交，RequestId: {}, TaskId: {}", result.getRequestId(), result.getTaskId());

            // 5. 阻塞等待任务完成
            // 使用 TranscriptionQueryParam 构建查询对象
            TranscriptionQueryParam queryParam = TranscriptionQueryParam.FromTranscriptionParam(param, result.getTaskId());

            // wait 方法会轮询直到任务结束 (SUCCEEDED, FAILED, CANCELED)
            result = transcription.wait(queryParam);

            // 6. 处理结果
            if (result.getOutput() != null) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String formattedResult = gson.toJson(result.getOutput());
                logger.info("转写完成: {}", formattedResult);
                return formattedResult;
            } else {
                throw new RuntimeException("转写失败");
            }

        } catch (Exception e) {
            logger.error("DashScope 转写异常", e);
            throw new RuntimeException("DashScope 转写异常: " + e.getMessage(), e);
        }
    }
}