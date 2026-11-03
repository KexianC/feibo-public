package com.hzmwmt.domain.ai.model.aggregate;

import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 统一策略聚合
 * @ClassName ApplicationAIAggregate
 * @Date: 2025/9/12 11:53
 */
@Builder
@Data
public class ApplicationAIAggregate {
    //回掉接口
    BiConsumer<String, String> onNext;
    Consumer<Throwable> onError;
    Consumer<String> onCompleteWithResult;
    private Long userId;
    private String sysWords;
    private String modelCode;
    private String modelName;
    private AIMessageEntity aiUserMessageEntity;
    private List<AIMessageEntity> aiMessageEntityHistoryList;
}
