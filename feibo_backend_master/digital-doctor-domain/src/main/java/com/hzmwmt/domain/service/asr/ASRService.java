package com.hzmwmt.domain.service.asr;

import com.hzmwmt.domain.callback.ASRCallback;

/**
 * 语音识别服务接口
 */
public interface ASRService {
    /**
     * 开始实时语音识别
     *
     * @param callback 结果回调监听器
     */
    void start(ASRCallback callback);

    /**
     * 停止识别
     */
    void stop();
}