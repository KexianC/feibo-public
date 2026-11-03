package com.hzmwmt.domain.ai.service.chat.model.strategy;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ModelProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.valobj.AIChatRoleEnumVO;
import com.hzmwmt.domain.ai.service.chat.model.IModelStrategyService;
import com.hzmwmt.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;

import java.util.*;
import java.util.stream.Collectors;

@Service("baichuan") // 服务标识：对应策略工厂的modelCode（必须为"baichuan"，与配置一致）
@Slf4j
public class BaichuanModelStrategyService extends AbstractModelStrategyService implements IModelStrategyService {

    /**
     * 百川模型的消息响应处理（流式调用）
     * 逻辑与Deepseek对齐，适配百川API参数规范及ApplicationAIAggregate结构
     */
    @Override
    public Disposable doMessageResponse(ModelProcessAggregate modelProcessAggregate) {
        // 1. 获取当前用户消息并校验
        AIMessageEntity aiUserMessageEntity = modelProcessAggregate.getAiMessageEntity();
        if (aiUserMessageEntity == null || StringUtils.isBlank(aiUserMessageEntity.getContent())) {
            log.error("当前用户消息为空或内容无效");
            modelProcessAggregate.getOnError().accept(new IllegalArgumentException("用户消息内容不能为空"));
            return () -> {
            };
        }

        // 2. 查询并处理历史消息（转换为百川要求的格式）
        List<AIMessageEntity> aiMessageEntities = queryHistoryMessageList(
                modelProcessAggregate.getUserId(),
                aiUserMessageEntity.getAiConversationId(),
                modelProcessAggregate.getAiModelConfigEntity().getAiModelConfigId(),
                modelProcessAggregate.getIsAllHistory()
        );
        // 转换历史消息为百川API要求的messages结构（基于aiChatRoleEnumVO区分角色）
        List<Map<String, String>> baichuanMessages = convertToBaichuanMessages(aiMessageEntities);

        // 3. 添加当前用户消息（用户角色固定为"user"）
        Map<String, String> currentUserMessage = new HashMap<>(2);
        currentUserMessage.put("role", "user");
        currentUserMessage.put("content", aiUserMessageEntity.getContent().trim());
        baichuanMessages.add(currentUserMessage);

        // 4. 校验模型名称
        String modelName = modelProcessAggregate.getAiModelConfigEntity().getName();
        if (!isValidBaichuanModel(modelName)) {
            log.error("百川模型名称无效: {}", modelName);
            modelProcessAggregate.getOnError().accept(new IllegalArgumentException("无效的百川模型名称: " + modelName));
            return () -> {
            };
        }

        // 5. 构建聚合参数（不使用extParam，基于现有字段传递，需aiPort内部适配解析）
        return aiPort.symptomAnalysis(ApplicationAIAggregate
                .builder()
                .userId(modelProcessAggregate.getUserId())
                .modelCode(modelProcessAggregate.getAiModelConfigEntity().getModelCode()) // "baichuan"
                .modelName(modelName) // 官方模型名
                .aiUserMessageEntity(aiUserMessageEntity)
                .aiMessageEntityHistoryList(aiMessageEntities) // 原始历史消息（供aiPort内部转换用）
                // 流式调用标识：通过sysWords暂存stream参数（若aiPort支持解析sysWords中的配置）
                // 若aiPort不支持，需在aiPort内部固定stream=true（因当前是流式调用场景）
                .sysWords(JSON.toJSONString(Map.of("stream", true)))
                .onError(error -> {
                    log.error("百川API调用失败: code={}, type={}",
                            ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), exceptionType(error));
                    modelProcessAggregate.getOnError().accept(error);
                })
                .onCompleteWithResult(modelProcessAggregate.getOnCompleteWithResult())
                .onNext(modelProcessAggregate.getOnNext())
                .build()
        );
    }

    /**
     * 转换历史消息为百川API格式（核心适配逻辑）
     */
    private List<Map<String, String>> convertToBaichuanMessages(List<AIMessageEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return new ArrayList<>();
        }
        return entities.stream()
                .filter(entity -> StringUtils.isNotBlank(entity.getContent())) // 过滤空内容
                .map(entity -> {
                    Map<String, String> message = new HashMap<>(2);
                    // 基于AIChatRoleEnumVO映射角色：false=user，true=assistant
                    AIChatRoleEnumVO roleEnum = entity.getAiChatRoleEnumVO();
                    // 假设AIChatRoleEnumVO的value是布尔值（false=用户，true=AI）
                    boolean isAssistant = roleEnum.getCode();  // 需与枚举实际字段匹配（例如getValue()返回布尔值）
                    message.put("role", isAssistant ? "assistant" : "user");
                    message.put("content", entity.getContent().trim());
                    return message;
                })
                .collect(Collectors.toList());
    }

    /**
     * 校验百川模型名称
     */
    private boolean isValidBaichuanModel(String modelName) {
        Set<String> validModels = Set.of(
                "Baichuan-M3-Plus",
                "Baichuan-M3",
                "Baichuan-M2-Plus",
                "Baichuan-M2",
                "Baichuan4-Turbo",
                "Baichuan4-Air",
                "Baichuan4",
                "Baichuan3-Turbo",
                "Baichuan3-Turbo-128k",
                "Baichuan2-Turbo"
        );
        return validModels.contains(modelName);
    }

    /**
     * 计算总Token数（历史消息+当前消息+格式消耗）
     */
    @Override
    public int calculateTotalTokens(
//            List<AIMessageEntity> historyMessages,
            AIMessageEntity currentMessage
    ) {
//        // 1. 计算历史消息Token
//        int historyTokens = 0;
//        if (historyMessages != null && !historyMessages.isEmpty()) {
//            for (AIMessageEntity msg : historyMessages) {
//                historyTokens += calculateSingleMessageTokens(msg.getContent());
//            }
//        }

        // 2. 计算当前消息Token
        int currentTokens = calculateSingleMessageTokens(
                currentMessage != null ? currentMessage.getContent() : ""
        );

//        // 3. 计算格式消耗Token（如角色名、分隔符）
//        int formatTokens = calculateFormatTokens(historyMessages.size());

        // 总Token = 历史 + 当前 + 格式
//        int totalTokens = historyTokens + currentTokens + formatTokens;

        log.info("Deepseek本地计算Token： 总[{}]",
                currentTokens);
        return currentTokens;
    }


    /**
     * 计算单条消息的Token数（核心逻辑）
     * 规则：
     * - 中文字符（含中文标点）：1个字符 ≈ 1个Token
     * - 英文字母/数字：连续字符按"每2个字符 ≈ 1个Token"（模拟子词合并）
     * - 英文单词间空格：不计入Token
     * - 特殊符号（如@#$%）：1个符号 ≈ 1个Token
     */
    @Override
    protected int calculateSingleMessageTokens(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }

        int tokenCount = 0;
        char[] chars = content.toCharArray();
        int englishDigitSequenceLength = 0; // 记录连续英文/数字的长度

        for (char c : chars) {
            if (isChineseChar(c)) {
                // 中文字符：直接+1 Token，同时结算之前的英文/数字序列
                tokenCount += 1 + (englishDigitSequenceLength > 0 ? (englishDigitSequenceLength + 1) / 2 : 0);
                englishDigitSequenceLength = 0; // 重置英文序列长度
            } else if (Character.isLetterOrDigit(c)) {
                // 英文/数字：累计长度，不立即算Token
                englishDigitSequenceLength++;
            } else if (Character.isWhitespace(c)) {
                // 空格：结算之前的英文/数字序列，空格本身不算Token
                tokenCount += (englishDigitSequenceLength > 0 ? (englishDigitSequenceLength + 1) / 2 : 0);
                englishDigitSequenceLength = 0;
            } else {
                // 特殊符号（如标点、@#等）：+1 Token，同时结算英文/数字序列
                tokenCount += 1 + (englishDigitSequenceLength > 0 ? (englishDigitSequenceLength + 1) / 2 : 0);
                englishDigitSequenceLength = 0;
            }
        }

        // 结算最后剩余的英文/数字序列
        tokenCount += (englishDigitSequenceLength > 0 ? (englishDigitSequenceLength + 1) / 2 : 0);

        return tokenCount;
    }

//    /**
//     * 计算格式消耗Token
//     * Deepseek对话格式：每条消息包含角色标识（如"user:"/"assistant:"）和分隔符
//     * 规则：每条消息固定消耗2个Token（角色1个 + 分隔符1个）
//     */
//    @Override
//    protected int calculateFormatTokens(int historyMessageCount) {
//        // 历史消息数 + 当前消息（1条），每条2个格式Token
//        return (historyMessageCount + 1) * 2;
//    }

    /**
     * 判断是否为中文字符（含中文标点）
     */
    private boolean isChineseChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS; // 全角符号
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }

}
