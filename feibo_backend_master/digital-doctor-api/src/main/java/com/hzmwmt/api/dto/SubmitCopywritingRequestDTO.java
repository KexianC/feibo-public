package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SubmitCopywritingRequestDTO
 * @Date: 2025/9/9 17:22
 */
@Data
public class SubmitCopywritingRequestDTO implements Serializable {

    @NotBlank(message = "文案标题不能为空")
    private String title;

    @NotBlank(message = "文案内容不能为空")
    private String content;

    @NotBlank(message = "文案摘要不能为空")
    private String summary;

    @NotNull(message = "文案分类ID不能为空")
    private Long copywritingCategoryId;

    @NotNull(message = "文案来源ID不能为空")
    private Long copywritingSourceId;

}
