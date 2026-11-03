package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class BaiduQianfanAdapter extends AbstractDelegatingPlatformAdapter {

    public BaiduQianfanAdapter() {
        super("baidu-qianfan", new String[]{"baidu-qianfan", "qianfan"}, new String[]{"qianfan-ai-search"});
    }
}
