package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class KimiAdapter extends AbstractDelegatingPlatformAdapter {

    public KimiAdapter() {
        super("kimi", new String[]{"kimi", "moonshot"}, new String[]{"kimi", "moonshot"});
    }
}
