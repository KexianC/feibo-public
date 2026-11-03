package com.hzmwmt.domain.gateway.vedio.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostVideoRequestDto {
    Integer type;
    String title;
    List<String > tags;
    List<String > fileList;
    List<String> accountList;
    Integer enableTimer;
    Integer videosPerDay;
    List<String > dailyTimes;
    Integer startDays;
    Integer category;
    String productLink;
    String productTitle;
    Boolean isDraft;
}
