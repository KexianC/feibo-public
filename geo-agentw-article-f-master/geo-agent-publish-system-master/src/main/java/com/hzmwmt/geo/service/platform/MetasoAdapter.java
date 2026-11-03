package com.hzmwmt.geo.service.platform;

import org.springframework.stereotype.Component;

@Component
public class MetasoAdapter extends AbstractDelegatingPlatformAdapter {

    public MetasoAdapter() {
        super("metaso", new String[]{"metaso", "metaso-ai"}, new String[]{"metaso"});
    }
}
