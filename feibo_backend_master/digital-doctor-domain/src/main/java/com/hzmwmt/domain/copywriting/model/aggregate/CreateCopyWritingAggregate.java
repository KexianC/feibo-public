package com.hzmwmt.domain.copywriting.model.aggregate;

import com.hzmwmt.domain.copywriting.model.entity.CopywritingEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CreateCopyWritingAggregate
 * @Date: 2025/9/9 14:27
 */
@Builder
@Data
public class CreateCopyWritingAggregate {
    private CopywritingEntity copywritingEntity;
    private Long copywritingCategoryId;
    private Long copywritingSourceId;
    private Long userId;
}
