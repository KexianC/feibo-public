package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminAiModelUpdateDTO {

    @Size(max = 128)
    private String name;

    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "供应商模型名称不能包含控制字符")
    private String modelName;

    @Size(max = 1000)
    private String description;

    @Min(1)
    @Max(10000)
    private Integer order;
}
