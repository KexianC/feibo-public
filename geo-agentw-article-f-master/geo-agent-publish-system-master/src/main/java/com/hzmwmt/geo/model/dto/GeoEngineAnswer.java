package com.hzmwmt.geo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoEngineAnswer {

    private String engine;

    private String answerText;

    private List<String> citedUrls;

    private String rawSearchInfo;

    private Integer sourceCount;

    private Integer rankPosition;

    private String sourceDetails;

    private Long latencyMs;

    public GeoEngineAnswer(String engine, String answerText) {
        this.engine = engine;
        this.answerText = answerText;
    }

    public GeoEngineAnswer(String engine, String answerText, List<String> citedUrls, String rawSearchInfo) {
        this.engine = engine;
        this.answerText = answerText;
        this.citedUrls = citedUrls;
        this.rawSearchInfo = rawSearchInfo;
    }
}
