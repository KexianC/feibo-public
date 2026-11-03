package com.hzmwmt.infrastructure.adapter.port;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.hzmwmt.domain.ai.adapter.port.IAIPort;
import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.entity.AIContentReviewResultEntity;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.AuthService;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.infrastructure.gateway.ai.openai.IOpenAIChatService;
import com.hzmwmt.infrastructure.gateway.ai.DatabaseBackedChatModelRegistry;
import com.hzmwmt.infrastructure.gateway.ai.tencent.ITencentAgentChatService;
import com.hzmwmt.infrastructure.gateway.ai.tencent.dto.ChatRequestDTO;
import com.hzmwmt.infrastructure.properties.TencentAgentProperties;
import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.util.SpringUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_AI_WRITE_LIST_PREFIX;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIPort
 * @Date: 2025/8/26 17:47
 */
@Slf4j
@Service
public class AIPort implements IAIPort {
    @Resource
    private IOpenAIChatService openAiChatService;
    @Resource
    private IRedisService redissonService;
    @Resource
    private ITencentAgentChatService tencentAgentChatService;
    @Resource
    private TencentAgentProperties tencentAgentProperties;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private AuthService authService;
    @Resource
    private SensitiveWordBs sensitiveWordBs;
    @Resource
    private DatabaseBackedChatModelRegistry chatModelRegistry;
    @Resource
    private com.hzmwmt.infrastructure.gateway.ai.ChatFailoverService chatFailoverService;


    @Override
    public Disposable symptomAnalysis(ApplicationAIAggregate applicationAiAggregate) {

        return chatFailoverService.stream(applicationAiAggregate);
    }


    @Override
    public Disposable symptomAnalysisByTencentAi(ApplicationAIAggregate applicationAiAggregate) {
        return chatFailoverService.stream(applicationAiAggregate, this::streamTencent);
    }

    private Disposable streamTencent(ApplicationAIAggregate applicationAiAggregate) {
        Long aiConversationId = applicationAiAggregate.getAiUserMessageEntity().getAiConversationId();
        if (aiConversationId == null) {
            aiConversationId = IdWorker.getId();
            applicationAiAggregate.getAiUserMessageEntity().setAiConversationId(aiConversationId);
        }

        return tencentAgentChatService.flowGenerate(
                ChatRequestDTO.builder()
                        .requestId(String.valueOf(UUID.randomUUID()))
                        .incremental(true)
                        .searchNetwork("enable")
                        .sessionId(String.valueOf(aiConversationId))
                        .botAppKey(tencentAgentProperties.getBotAppKey())
                        .visitorBizId(String.valueOf(applicationAiAggregate.getUserId()))
                        .content(applicationAiAggregate.getAiUserMessageEntity().getContent())
                        .build(),
                new ITencentAgentChatService.TencentSseCallback() {

                    @Override
                    public void onReply(String type, String content) {
                        applicationAiAggregate.getOnNext().accept(type, content);
                    }

                    @Override
                    public void onError(Throwable error) {
                        applicationAiAggregate.getOnError().accept(error);
                    }

                    @Override
                    public void onComplete(String finalFullResponse) {
                        applicationAiAggregate.getOnCompleteWithResult().accept(finalFullResponse);
                    }
                }
        );
    }

    @Override
    public List<String> getAiRecommendSearch() {
        String specialty = adminUserService.getSpecialtyByUserId(authService.queryValidatedUser().getUserId());
        log.info("用户 {} 获取AI推荐搜索", authService.queryValidatedUser().getUserId());
        String systemPromptTemplate = String.format("""
                你是医疗搜索推荐系统。
                
                根据医生的专业方向，生成5个用户可能搜索的问题。
                
                要求：
                1. 与医疗健康相关
                2. 不包含诊断结论
                3. 每条不超过15字
                
                返回格式：
                只返回 JSON 数组（List<String>），不要返回对象，不要包含任何解释。
                
                示例：
                [
                "高血压怎么预防",
                "高血压饮食注意什么",
                "高血压需要长期吃药吗",
                "高血压能运动吗",
                "高血压有哪些症状",
                "高血压多久复查一次",
                "高血压和遗传有关吗",
                "高血压能吃什么水果"
                ]
                
                医生专业方向：%s
                """, specialty);

        // 推荐问题使用当前 Flash 版本。
        ChatModel model = SpringUtil.getBean("qwen" + Constants.AIChatConstants.MODEL_SUFFIX, ChatModel.class);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptTemplate));
        Prompt prompt;
        ChatOptions options = OpenAiChatOptions.builder()
                .model("qwen3.7-flash")
                // 如果需要，还可以在这里设置其他参数，例如温度
                // .withTemperature(0.7f)
                .build();
        prompt = new Prompt(messages, options);
        String response = openAiChatService.generate(model, prompt);
        return JSON.parseArray(response, String.class);
    }

//    @Override
//    public void symptomAnalysisByWiseDiag(ApplicationAIAggregate applicationAiAggregate) {
//        List<AIMessageEntity> messageEntityHistoryList = applicationAiAggregate.getAiMessageEntityHistoryList();
//        AIMessageEntity aiUserMessageEntity = applicationAiAggregate.getAiUserMessageEntity();
//        List<WiseDiagChatRequestDTO.WiseDiagMessageDTO> messages = new ArrayList<>();
//        if(messageEntityHistoryList !=null&& !messageEntityHistoryList.isEmpty()){
//            for (AIMessageEntity msg : messageEntityHistoryList) {
//                WiseDiagChatRequestDTO.WiseDiagMessageDTO wiseDiagMessageDTO = WiseDiagChatRequestDTO.WiseDiagMessageDTO.builder()
//                        .role(msg.getAiChatRoleEnumVO().name())
//                        .content(msg.getContent())
//                        .build();
//                messages.add(wiseDiagMessageDTO);
//            }
//        }
//        messages.add(
//                WiseDiagChatRequestDTO.WiseDiagMessageDTO.builder()
//                        .role(AIChatRoleEnumVO.USER.name())
//                        .content(aiUserMessageEntity.getContent())
//                        .build()
//        );
//        wiseDiagChatService.flowGenerate(
//                WiseDiagChatRequestDTO.builder()
//                        .model(aiUserMessageEntity.getAiModelEnumVO().getName())
//                        .messages(messages)
//                        .stream(true)
//                        .temperature(0.7f)
//                        .topP(1.0f)
//                        .maxTokens(1024)
//                        .build(),
//                new IWiseDiagChatService.WiseDiagSseCallback() {
//                    @Override
//                    public void onReply(String reply) {
//                        applicationAiAggregate.getOnNext().accept( reply);
//                    }
//
//                    @Override
//                    public void onError(Throwable error) {
//                        applicationAiAggregate.getOnError().accept(error);
//                    }
//
//                    @Override
//                    public void onComplete(String finalFullResponse) {
//                        applicationAiAggregate.getOnCompleteWithResult().accept(finalFullResponse);
//                    }
//                }
//
//        );
//    }

//    @Override
//    public void symptomAnalysisByDouBao(ApplicationAIAggregate applicationAiAggregate) {
//        List<AIMessageEntity> messageEntityHistoryList = applicationAiAggregate.getAiMessageEntityHistoryList();
//        AIMessageEntity aiUserMessageEntity = applicationAiAggregate.getAiUserMessageEntity();
//        List<ChatMessage> messages = new ArrayList<>();
//        if(messageEntityHistoryList !=null&& !messageEntityHistoryList.isEmpty()){
//            for (AIMessageEntity msg : messageEntityHistoryList) {
//            ChatMessage  message = ChatMessage.builder()
//                        .role(msg.getAiChatRoleEnumVO().equals(AIChatRoleEnumVO.USER)? ChatMessageRole.USER:ChatMessageRole.ASSISTANT)
//                        .content(msg.getContent())
//                        .build();
//                messages.add(message);
//            }
//        }
//        messages.add(
//                ChatMessage.builder()
//                        .role(ChatMessageRole.SYSTEM)
//                        .content("\"你是一个专业的AI医疗助手。\" +\n" +
//                                "\"请必须使用Markdown格式来组织和美化你的回答。\" +\n")
//                        .build()
//        );
//        douBaoChatService.flowGenerate(
//                ChatCompletionRequest.builder()
//                        .model(aiUserMessageEntity.getAiModelEnumVO().getName())
//                        .messages(messages)
//                        .stream(true)
//                        .temperature(0.7)
//                        .topP(1.0)
//                        .maxTokens(1024)
//                        .presencePenalty(0.0)
//                        .frequencyPenalty(0.0)
//                        .logprobs(false)
//                        .build(),
//                new IDouBaoChatService.DouBaoSseCallback() {
//                    @Override
//                    public void onReply(String reply) {
//                        applicationAiAggregate.getOnNext().accept( reply);
//                    }
//
//                    @Override
//                    public void onError(Throwable error) {
//                        applicationAiAggregate.getOnError().accept(error);
//                    }
//                    @Override
//                    public void onComplete(String finalFullResponse) {
//                        applicationAiAggregate.getOnCompleteWithResult().accept(finalFullResponse);
//                    }
//                }
//        );
//    }

    @Override
    public Boolean verifyAiQuota(Long userId) {
        Double expireTimestamp = redissonService.getScoreFromScoredSet(KEY_AI_WRITE_LIST_PREFIX, userId);


        // 如果时间戳为 null，说明从未领取过，返回 false
        if (expireTimestamp == null) {
            return false;
        }

        // 检查是否过期：当前时间小于过期时间戳才有效
        return System.currentTimeMillis() < expireTimestamp.longValue();
    }

    @Override
    public AIContentReviewResultEntity reviewContent(String content) {
        if (content == null) {
            content = "";
        }

        // 1) 敏感词检测（本地词库）
        List<String> sensitiveWords = sensitiveWordBs == null ? new ArrayList<>() : sensitiveWordBs.findAll(content);

        // 2) 调用大模型区分“修改建议”与“原则性错误”（要求只返回 JSON）
        ChatModel model = SpringUtil.getBean("qwen" + Constants.AIChatConstants.MODEL_SUFFIX, ChatModel.class);
        List<Message> messages = new ArrayList<>();
        String systemPrompt = """
                你是一个医疗内容审核助手，需要对下面的一段内容进行审核，输出严格的 JSON，格式如下：
                 {
                   "typos": ["错别字或用词问题说明..."],
                   "scientificIssues": ["非阻断性的医学严谨性提醒..."],
                   "principleErrors": ["原则性错误：引用原文关键表述并说明严重风险..."],
                   "suggestion": "整体修改建议，不超过200字",
                   "pass": true
                 }
                
                要求：
                 1. 只返回 JSON 字符串，不要任何额外解释；
                 2. 如果没有问题，对应数组返回空数组；
                 3. 只有存在“原则性错误”时才能 pass=false，并将具体依据写入 principleErrors；
                 4. 原则性错误仅包括：明确的重大医学事实错误、可能造成严重伤害的诊疗/用药建议、
                    鼓励停用必要治疗或替代正规就医、明确违法违规或严重危害公众安全的内容；
                 5. 错别字、语句不通顺、表达可优化、缺少引用、普通风险提示、轻微不严谨但不改变医学结论，
                    以及仅因文章包含疾病、药品、手术等医学词汇，都不属于原则性错误；
                 6. 只有修改建议或非阻断性提醒时，principleErrors 必须为空数组且 pass 必须为 true；
                 7. pass 必须与 principleErrors 一致：principleErrors 为空时 pass=true，非空时 pass=false。
                 """;
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(content));

        ChatOptions options = OpenAiChatOptions.builder()
                .model("qwen3.7-flash")
                .temperature(0.1)
                .build();
        Prompt prompt = new Prompt(messages, options);
        String aiResponse = openAiChatService.generate(model, prompt);

        return parseContentReviewResult(aiResponse, sensitiveWords);
    }

    static AIContentReviewResultEntity parseContentReviewResult(String aiResponse, List<String> sensitiveWords) {
        try {
            if (aiResponse == null || aiResponse.isBlank()) {
                throw new IllegalStateException("AI content review returned an empty response");
            }
            JSONObject json = JSONObject.parseObject(aiResponse);
            if (json == null || !json.containsKey("pass") || json.getBoolean("pass") == null) {
                throw new IllegalStateException("AI content review response is missing the required pass field");
            }
            List<String> typos = Optional.ofNullable(json.getJSONArray("typos"))
                    .map(arr -> arr.toJavaList(String.class))
                    .orElseGet(ArrayList::new);
            List<String> scientificIssues = Optional.ofNullable(json.getJSONArray("scientificIssues"))
                    .map(arr -> arr.toJavaList(String.class))
                    .orElseGet(ArrayList::new);
            if (!json.containsKey("principleErrors") || json.getJSONArray("principleErrors") == null) {
                throw new IllegalStateException("AI content review response is missing the required principleErrors field");
            }
            List<String> principleErrors = json.getJSONArray("principleErrors").toJavaList(String.class).stream()
                    .filter(issue -> issue != null && !issue.isBlank())
                    .toList();
            principleErrors.forEach(issue -> scientificIssues.add("原则性错误：" + issue));

            // The workflow decision is derived from concrete blocking issues instead
            // of the model's subjective pass flag. Suggestions and ordinary review
            // notes are therefore recorded without rejecting the copywriting.
            boolean finalPass = principleErrors.isEmpty();
            return AIContentReviewResultEntity.builder()
                    .pass(finalPass)
                    .sensitiveWords(sensitiveWords)
                    .typos(typos)
                    .scientificIssues(scientificIssues)
                    .suggestion(json.getString("suggestion"))
                    .build();
        } catch (Exception e) {
            log.error("解析 AI 内容审核结果失败: type={}", e.getClass().getSimpleName());
            // Fail closed. The immediate-review dispatcher will leave the flow in
            // AI_PENDING and retry instead of silently advancing unaudited content.
            throw new IllegalStateException("AI内容审核结果无效", e);
        }
    }

}
