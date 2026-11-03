package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdateDigitalVoiceFavoriteStatusRequestDTO
 * @Date: 2025/9/24 23:23
 */
@Data
public class UpdateDigitalVoiceFavoriteStatusRequestDTO {
    @NotNull(message = "声音ID不能为空")
    private Long voiceId;

    @NotNull(message = "收藏状态不能为空")
    private Boolean collected;
}
