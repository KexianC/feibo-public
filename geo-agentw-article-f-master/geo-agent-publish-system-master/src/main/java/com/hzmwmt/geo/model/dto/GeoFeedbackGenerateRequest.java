package com.hzmwmt.geo.model.dto;

import lombok.Data;

@Data
public class GeoFeedbackGenerateRequest {

    private Long queryId;

    private Long monitorRecordId;

    private Integer limit;
}
