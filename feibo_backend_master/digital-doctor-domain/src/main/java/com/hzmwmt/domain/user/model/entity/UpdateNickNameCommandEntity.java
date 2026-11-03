package com.hzmwmt.domain.user.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdateNickNameCommandEntity
 * @Date: 2025/9/17 12:47
 */
@Data
@Builder
public class UpdateNickNameCommandEntity {
    @Schema(description = "用户业务ID（userId）", example = "1976200871068037122")
    private Long userId;

    @Schema(description = "新昵称", example = "新昵称_星辰大海")
    private String newNickName;
}