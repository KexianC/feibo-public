package com.hzmwmt.geo.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class GeoQueryGenerateRequest {

    private Long doctorId;

    private Long departmentId;

    private Long hospitalId;

    private String region;

    private List<String> keywords;

    private List<String> diseases;

    private List<String> treatmentScenarios;

    private Integer priority;

    private Integer monitorIntervalHours;

    private Boolean persist;
}
