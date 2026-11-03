package com.hzmwmt.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DigitalHumanVideoCallbackRequestDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    @JsonAlias({"id", "taskId"})
    private String taskId;

    @NotBlank
    private String status;

    private String statusDesc;
    private String errorMessage;
    private String resultUrl;
    private String coverUrl;
    private String name;
    private Long duration;
    private Integer width;
    private Integer height;
    private String created;
}
