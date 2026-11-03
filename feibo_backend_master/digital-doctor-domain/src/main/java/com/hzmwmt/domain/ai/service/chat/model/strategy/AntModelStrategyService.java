package com.hzmwmt.domain.ai.service.chat.model.strategy;

import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ModelProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.service.chat.model.IModelStrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.List;

@Slf4j
@Service("ant")
public class AntModelStrategyService extends AbstractModelStrategyService implements IModelStrategyService {
    @Override
    public Disposable doMessageResponse(ModelProcessAggregate modelProcessAggregate) {
        AIMessageEntity aiUserMessageEntity = modelProcessAggregate.getAiMessageEntity();
        List<AIMessageEntity> aiMessageEntities = queryHistoryMessageList(modelProcessAggregate.getUserId(), aiUserMessageEntity.getAiConversationId(), modelProcessAggregate.getAiModelConfigEntity().getAiModelConfigId(), modelProcessAggregate.getIsAllHistory());
        return aiPort.symptomAnalysis(ApplicationAIAggregate
                .builder()
                .userId(modelProcessAggregate.getUserId())
                .modelCode(modelProcessAggregate.getAiModelConfigEntity().getModelCode())
                .modelName(modelProcessAggregate.getAiModelConfigEntity().getName())
                .aiUserMessageEntity(aiUserMessageEntity)
                .aiMessageEntityHistoryList(aiMessageEntities)
                .onError(modelProcessAggregate.getOnError())
                .onCompleteWithResult(modelProcessAggregate.getOnCompleteWithResult())
                .onNext(modelProcessAggregate.getOnNext())
                .build()
        );
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

}
