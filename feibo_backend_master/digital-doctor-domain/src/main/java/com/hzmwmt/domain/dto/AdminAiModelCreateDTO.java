package com.hzmwmt.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class AdminAiModelCreateDTO {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,31}$")
    private String modelCode;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "供应商模型名称不能包含控制字符")
    private String modelName;

    @Size(max = 1000)
    private String description;

    private Boolean visible;

    @Min(1)
    @Max(10000)
    private Integer order;

    @Size(max = 4096)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String apiKey;
}
