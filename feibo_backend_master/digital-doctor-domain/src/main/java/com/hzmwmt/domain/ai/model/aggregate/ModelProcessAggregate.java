package com.hzmwmt.domain.ai.model.aggregate;

import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import lombok.Builder;
import lombok.Data;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ModelProcessAggregate
 * @Date: 2025/9/23 1:09
 */
@Builder
@Data
public class ModelProcessAggregate {
    BiConsumer<String, String> onNext;
    Consumer<Throwable> onError;
    Consumer<String> onCompleteWithResult;
    private Long userId;
    private AIModelConfigEntity aiModelConfigEntity;
    private Boolean isAllHistory;
    private AIMessageEntity aiMessageEntity;
}
