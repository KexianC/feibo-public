package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class QwenAdapter extends AbstractDelegatingPlatformAdapter {

    public QwenAdapter() {
        super("qwen", new String[]{"qwen", "aliyun-qwen"}, new String[]{"dashscope"});
    }
}
