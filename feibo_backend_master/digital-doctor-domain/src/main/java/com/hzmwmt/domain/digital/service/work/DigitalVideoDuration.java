package com.hzmwmt.domain.digital.service.work;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;

import java.util.function.Function;

/** Server-side reservation estimate; settlement always uses authenticated provider duration. */
public final class DigitalVideoDuration {
    private DigitalVideoDuration() {}

    public static long estimateSeconds(String body, Function<String, Integer> ownedAudioDuration) {
        VideoCompositionPayloadValidator.validate(body);
        JSONObject payload = JSON.parseObject(body);
        JSONArray items = payload.getJSONArray("items");
        if (items.size() > 100) throw invalid("最多支持100个合成场景");
        long total = 0;
        for (int i = 0; i < items.size(); i++) {
            JSONArray details = items.getJSONObject(i).getJSONArray("detailList");
            long scene = 0;
            if (details == null) continue;
            if (details.size() > 200) throw invalid("场景内容过多");
            for (int j = 0; j < details.size(); j++) {
                JSONObject detail = details.getJSONObject(j);
                if (detail == null) continue;
                String type = detail.getString("contentType");
                String content = detail.getString("content");
                if ("text".equalsIgnoreCase(type)) {
                    double speed = detail.containsKey("speed") ? detail.getDoubleValue("speed") : 1.0;
                    if (!Double.isFinite(speed) || speed < 0.5 || speed > 2) throw invalid("语速必须在0.5到2之间");
                    // Conservative two characters/second plus pause allowance, independent of videoTime.
                    long textSeconds = (long) Math.ceil(content.codePointCount(0, content.length()) / (2.0 * speed)) + 2;
                    scene = Math.addExact(scene, textSeconds);
                } else if ("audio".equalsIgnoreCase(type)) {
                    Integer seconds = ownedAudioDuration.apply(content);
                    if (seconds == null || seconds <= 0 || seconds > 86400)
                        throw invalid("音频必须是本账号已上传且已识别时长的文件，请重新上传");
                    scene = Math.addExact(scene, seconds);
                }
                // Timeline values are durations actually forwarded upstream, never a client quota hint.
                for (String field : new String[]{"startTime", "stopTime"}) {
                    if (!detail.containsKey(field)) continue;
                    double value = detail.getDoubleValue(field);
                    if (!Double.isFinite(value) || value < 0 || value > 86400000)
                        throw invalid("场景时间参数无效");
                    scene = Math.max(scene, (long) Math.ceil(value / 1000.0));
                }
            }
            total = Math.addExact(total, scene);
            if (total > 86400) throw invalid("视频最长支持24小时");
        }
        if (total <= 0) throw invalid("无法确定视频时长");
        return total;
    }

    public static int normalize(Long duration, String unit) {
        if (duration == null || duration <= 0) throw invalid("供应商未返回有效视频时长，请稍后刷新");
        if ("milliseconds".equalsIgnoreCase(unit) && duration <= 86400000)
            return Math.toIntExact((duration + 999) / 1000);
        if ("seconds".equalsIgnoreCase(unit) && duration <= 86400)
            return Math.toIntExact(duration);
        throw invalid("供应商视频时长或单位配置无效");
    }

    private static AppException invalid(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
