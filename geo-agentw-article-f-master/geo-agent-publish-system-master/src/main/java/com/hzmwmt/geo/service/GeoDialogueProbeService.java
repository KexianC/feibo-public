package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoProbeRunRequest;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;

import java.util.List;

public interface GeoDialogueProbeService {

    List<GeoDialogueProbe> schedule(GeoProbeRunRequest request);

    IPage<GeoDialogueProbe> page(Page<GeoDialogueProbe> page,
                                 Integer status,
                                 String engine,
                                 String probeMode,
                                 String gapType);
}
