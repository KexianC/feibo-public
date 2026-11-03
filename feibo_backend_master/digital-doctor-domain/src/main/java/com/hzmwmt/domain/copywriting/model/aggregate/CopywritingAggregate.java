package com.hzmwmt.domain.copywriting.model.aggregate;

import com.hzmwmt.domain.copywriting.model.entity.CopywritingCategoryEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingSourceEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingAggregate
 * @Date: 2025/9/7 13:09
 */
@Builder
@Data
public class CopywritingAggregate {
    /**
     * 文案实体
     */
    private CopywritingEntity copywritingEntity;

    /**
     * 文案分类实体
     */
    private CopywritingCategoryEntity copywritingCategoryEntity;

    /**
     * 文案来源实体
     */
    private CopywritingSourceEntity copywritingSourceEntity;


}
