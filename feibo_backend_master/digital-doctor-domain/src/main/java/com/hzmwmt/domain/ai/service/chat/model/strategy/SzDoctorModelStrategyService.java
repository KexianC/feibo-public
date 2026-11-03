package com.hzmwmt.domain.ai.service.chat.model.strategy;

import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ModelProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;

/**
 * Maps user-facing Chinese model names to the SZDoctor OpenAPI model aliases.
 */
@Service("szdoctor")
public class SzDoctorModelStrategyService extends QWenModelStrategyService {

    private static final Map<String, String> UPSTREAM_MODELS = Map.ofEntries(
            Map.entry("木吾木同MedTalent", "szDoctor-7B"),
            Map.entry("木吾木同·MedTalent 健康科普大模型", "szDoctor-7B"),
            Map.entry("01老年健康科普分库", "szDoctor-dept-01"),
            Map.entry("02眼底病与白内障健康科普分库", "szDoctor-dept-02"),
            Map.entry("03头颈甲状腺外科科普分库", "szDoctor-dept-03"),
            Map.entry("04乳腺外科科普分库", "szDoctor-dept-04"),
            Map.entry("05肿瘤防治科普分库", "szDoctor-dept-05"),
            Map.entry("06心理健康科普分库", "szDoctor-dept-06"),
            Map.entry("07结核病防治科普分库", "szDoctor-dept-07"),
            Map.entry("08艾滋病防治科普分库", "szDoctor-dept-08"),
            Map.entry("09免疫规划科普分库", "szDoctor-dept-09"),
            Map.entry("10控烟科普分库", "szDoctor-dept-10"),
            Map.entry("11妇女保健科普分库", "szDoctor-dept-11"),
            Map.entry("12儿童保健科普分库", "szDoctor-dept-12"),
            Map.entry("13口腔保健科普分库", "szDoctor-dept-13"),
            Map.entry("14中医保健科普分库", "szDoctor-dept-14"),
            Map.entry("15慢病（体重）管理科普分库", "szDoctor-dept-15"),
            Map.entry("16视觉健康科普分库", "szDoctor-dept-16"),
            Map.entry("17慢性心血管疾病管理科普分库", "szDoctor-dept-17"),
            Map.entry("18颈肩腰腿病科普分库", "szDoctor-dept-18"),
            Map.entry("19肿瘤与营养科普分库", "szDoctor-dept-19"),
            Map.entry("20骨科健康科普分库", "szDoctor-dept-20"),
            Map.entry("21皮肤病与医学美容科普分库", "szDoctor-dept-21"),
            Map.entry("22呼吸健康科普分库", "szDoctor-dept-22"),
            Map.entry("23护理科普分库", "szDoctor-dept-23"),
            Map.entry("24运动促进健康科普分库", "szDoctor-dept-24"),
            Map.entry("25药学健康科普分库", "szDoctor-dept-25")
    );

    @Override
    public Disposable doMessageResponse(ModelProcessAggregate modelProcessAggregate) {
        AIMessageEntity aiUserMessageEntity = modelProcessAggregate.getAiMessageEntity();
        List<AIMessageEntity> history = queryHistoryMessageList(
                modelProcessAggregate.getUserId(),
                aiUserMessageEntity.getAiConversationId(),
                modelProcessAggregate.getAiModelConfigEntity().getAiModelConfigId(),
                modelProcessAggregate.getIsAllHistory()
        );
        String displayName = modelProcessAggregate.getAiModelConfigEntity().getName();
        String upstreamModel = UPSTREAM_MODELS.getOrDefault(displayName, displayName);

        return aiPort.symptomAnalysis(ApplicationAIAggregate.builder()
                .userId(modelProcessAggregate.getUserId())
                .modelCode(modelProcessAggregate.getAiModelConfigEntity().getModelCode())
                .modelName(upstreamModel)
                // Legacy description remains runtime-only. The administrator
                // edits displayDescription, so UI copy cannot change prompts.
                .sysWords(modelProcessAggregate.getAiModelConfigEntity().getDescription())
                .aiUserMessageEntity(aiUserMessageEntity)
                .aiMessageEntityHistoryList(history)
                .onError(modelProcessAggregate.getOnError())
                .onCompleteWithResult(modelProcessAggregate.getOnCompleteWithResult())
                .onNext(modelProcessAggregate.getOnNext())
                .build());
    }
}
