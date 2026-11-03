package com.hzmwmt.domain.controller.asr;

import com.hzmwmt.domain.callback.ASRCallback;
import com.hzmwmt.domain.service.asr.ASRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/asr")
@Profile("asr-prototype")
public class AsrController {

    private static final Logger logger = LoggerFactory.getLogger(AsrController.class);

    @Autowired
    private ASRService asrService;

    // 用来存放当前的连接，方便停止时操作（简单演示只支持单例）
    private SseEmitter currentEmitter;

    /**
     * 启动录音并通过 SSE 推送结果
     */
    @GetMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startRecognition() {
        // 创建一个 SSE 发射器，超时时间设为 0 (永不超时) 或设置大一点
        SseEmitter emitter = new SseEmitter(0L);
        this.currentEmitter = emitter;

        // 定义回调
        ASRCallback callback = new ASRCallback() {
            @Override
            public void onPartialResult(String text) {
                // 发送正在说话的中间结果，事件名为 "partial"
                sendSseEvent(emitter, "partial", text);
            }

            @Override
            public void onFinalResult(String text) {
                // 发送确定的一句话，事件名为 "final"
                sendSseEvent(emitter, "final", text);
            }

            @Override
            public void onError(Exception e) {
                logger.error("语音识别失败", e);
                sendSseEvent(emitter, "error", "语音识别失败，请稍后重试");
                emitter.complete();
            }

            @Override
            public void onComplete() {
                sendSseEvent(emitter, "complete", "识别结束");
                emitter.complete();
            }
        };

        try {
            sendSseEvent(emitter, "partial", "服务端已就绪，正在监听服务器麦克风...");
            asrService.start(callback);
        } catch (Exception e) {
            logger.error("启动语音识别失败", e);

            // 1. 优雅地告诉前端报错了
            try {
                emitter.send(SseEmitter.event().name("error").data("启动失败: 阿里云Key无效或网络错误"));
            } catch (IOException ex) {
                // ignore
            }

            // 2. 已发送稳定错误事件，直接结束连接，避免将底层异常再次交给 HTTP 错误处理链。
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 手动停止录音接口
     */
    @GetMapping("/stop")
    public String stopRecognition() {
        asrService.stop();
        if (currentEmitter != null) {
            currentEmitter.complete();
        }
        return "Stopped";
    }

    // 辅助方法：发送 SSE 消息
    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IllegalStateException e) {
            // 🌟 核心修改 1：捕获 "ResponseBodyEmitter has already completed"
            // 这说明连接已经正常关闭了，后台线程还在发消息。
            // 这是正常现象，直接忽略，不要打印堆栈，保持日志干净。
        } catch (IOException e) {
            // 🌟 核心修改 2：捕获 IO 异常（比如浏览器直接关掉了网页）
            // 也是正常现象，忽略即可。
        } catch (Exception e) {
            // 其他未知异常才打印出来
            logger.error("SSE 发送未知异常", e);
        }
    }
}
