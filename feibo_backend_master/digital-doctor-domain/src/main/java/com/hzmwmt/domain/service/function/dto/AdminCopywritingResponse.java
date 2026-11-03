package com.hzmwmt.domain.service.function.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCopywritingResponse {
    private Long id;
    private String submitterIdentity;
    private LocalDateTime time;
    private String submitterName;
    private String title;
    private String status;
    private String evaluation;
    private Long copywritingId;
}