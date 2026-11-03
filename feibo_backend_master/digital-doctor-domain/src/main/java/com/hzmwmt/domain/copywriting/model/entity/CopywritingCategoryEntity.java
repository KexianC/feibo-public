package com.hzmwmt.domain.copywriting.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingCategoryEntity
 * @Date: 2025/9/7 13:06
 */
@Builder
@Data
public class CopywritingCategoryEntity {
    private Long copywritingCategoryId;
    private String name;
}
