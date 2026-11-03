package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class VolcDoubaoAdapter extends AbstractDelegatingPlatformAdapter {

    public VolcDoubaoAdapter() {
        super("volc-doubao", new String[]{"volc-doubao", "doubao"}, new String[]{"volc-doubao", "doubao"});
    }
}
