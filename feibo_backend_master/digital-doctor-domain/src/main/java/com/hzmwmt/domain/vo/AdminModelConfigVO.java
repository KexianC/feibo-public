package com.hzmwmt.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI模型配置的展示VO（用于返回模型的基础信息）
 */
@Data
@Schema(description = "AI模型配置的展示视图对象，用于返回模型的ID和名称等基础信息")
public class AdminModelConfigVO {

    @Schema(
            description = "模型ID（主键，唯一标识一个AI模型）",
            example = "1001"
    )
    private Long id;

    @Schema(
            description = "模型名称（如GPT-4、文心一言、讯飞星火等）",
            example = "GPT-4"
    )
    private String name;

    public AdminModelConfigVO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
