package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class BochaAdapter extends AbstractDelegatingPlatformAdapter {

    public BochaAdapter() {
        super("bocha", new String[]{"bocha", "bocha-search"}, new String[]{"bocha"});
    }
}
