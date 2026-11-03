package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdateDigitalWorkTitleRequestDTO
 * @Date: 2025/9/24 21:39
 */
@Data
public class UpdateDigitalWorkTitleRequestDTO {
    @NotNull(message = "作品ID不能为空")
    private Long digitalWorkId;
    @NotBlank(message = "作品标题不能为空")
    private String title;
}
