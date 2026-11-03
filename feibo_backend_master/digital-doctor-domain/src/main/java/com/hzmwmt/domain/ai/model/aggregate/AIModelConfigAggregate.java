package com.hzmwmt.domain.ai.model.aggregate;

import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIModelConfigAggregate
 * @Date: 2025/9/22 23:40
 */
@Builder
@Data
public class AIModelConfigAggregate {
    AIModelConfigEntity data;
    List<AIModelConfigAggregate> childrenEntityList;
}
