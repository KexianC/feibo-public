package com.hzmwmt.domain.callback;

/**
 * 语音识别结果回调接口
 */
public interface ASRCallback {
    /**
     * 实时中间结果（句子还没说完，结果可能会变）
     *
     * @param text 识别到的文本
     */
    void onPartialResult(String text);

    /**
     * 最终结果（一句话结束，结果确定）
     *
     * @param text 识别到的文本
     */
    void onFinalResult(String text);

    /**
     * 发生错误时调用
     *
     * @param e 异常信息
     */
    void onError(Exception e);

    /**
     * 识别任务彻底结束时调用
     */
    void onComplete();
}