package com.hzmwmt.geo.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class GeoProbeRunRequest {

    private List<Long> queryIds;
    private List<String> engines;
    private Integer limit;
    private Integer rounds;
}
