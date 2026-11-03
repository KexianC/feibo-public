package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class ZhipuWebSearchAdapter extends AbstractDelegatingPlatformAdapter {

    public ZhipuWebSearchAdapter() {
        super("zhipu-web-search", new String[]{"zhipu", "zhipu-web-search"}, new String[]{"zhipu-web-search"});
    }
}
