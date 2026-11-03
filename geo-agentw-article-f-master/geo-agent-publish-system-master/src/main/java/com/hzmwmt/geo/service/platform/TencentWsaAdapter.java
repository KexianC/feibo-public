package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class TencentWsaAdapter extends AbstractDelegatingPlatformAdapter {

    public TencentWsaAdapter() {
        super("tencent-wsa", new String[]{"tencent-wsa", "tencent"}, new String[]{"tencent-wsa"});
    }
}
