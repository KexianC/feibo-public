package com.hzmwmt.ai.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.ai.assistant.Assistant;
import com.hzmwmt.ai.rag.config.RagProperties;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.model.dto.KnowledgeSearchHit;
import com.hzmwmt.ai.rag.model.entity.ArticleGenerationRecord;
import com.hzmwmt.ai.rag.service.RagChatService;
import com.hzmwmt.ai.rag.service.RagKnowledgeBaseService;
import com.hzmwmt.ai.rag.mapper.ArticleGenerationRecordMapper;
import com.hzmwmt.ai.rag.service.KnowledgeAgentScopeResolver;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagChatServiceImpl implements RagChatService {

    @Resource
    private Assistant assistant;

    @Resource
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private ArticleGenerationRecordMapper articleGenerationRecordMapper;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private KnowledgeAgentScopeResolver scopeResolver;

    @Override
    public Flux<String> chat(String userMessage, Long doctorId) {
        return chat(userMessage, scopeResolver.resolve(null, null, null, doctorId));
    }

    @Override
    public Flux<String> chat(String userMessage, KnowledgeAgentScope scope) {
        if (!ragProperties.isEnabled()) {
            return assistant.chat(userMessage);
        }

        // 先检索知识片段，再拼装成最终提示词交给模型。
        List<KnowledgeSearchHit> hits = ragKnowledgeBaseService.search(userMessage, ragProperties.getTopK(), scope);
        // 构建知识来源Json
        String knowledgeSourcesJson = hits.isEmpty() ? "[]" : hits.stream().map(h -> StringUtils.hasText(h.getTitle()) ? h.getTitle() : "未知文件").distinct().collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
            try {
                return objectMapper.writeValueAsString(list);
            } catch (Exception e) {
                return "[]";
            }
        }));
        // 提前插入生成记录
        Long recordId = snowflakeIdGenerator.nextId();
        ArticleGenerationRecord genRecord = ArticleGenerationRecord.builder()
                .id(recordId)
                .agentType(scope.getAgentType())
                .hospitalId(scope.getHospitalId())
                .hospitalName(scope.getHospitalName())
                .departmentId(scope.getDepartmentId())
                .departmentName(scope.getDepartmentName())
                .doctorId(scope.getDoctorId())
                .doctorName(scope.getDoctorName())
                .queryText(userMessage)
                .knowledgeSources(knowledgeSourcesJson)
                .status(1)   // 先标记为成功，若最终失败再改为2
                .build();
        articleGenerationRecordMapper.insert(genRecord);
        // 构建提示词，调用大模型，并累积返回内容
        String prompt = buildPrompt(userMessage, hits, scope);
        Flux<String> chatStream = assistant.chat(prompt);
        StringBuilder fullContent = new StringBuilder();
        return chatStream.doOnNext(fullContent::append).doOnError(e -> updateRecordFailed(recordId, "生成异常：" + e.getMessage())).doOnComplete(() -> {
            String responseText = normalizeJson(fullContent.toString());
            try {
                JsonNode root = objectMapper.readTree(responseText);
                String title = root.path("title").asText("");
                String content = root.path("content").asText("");

                if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
                    updateRecordFailed(recordId, "模型未生成有效文章，返回内容：" + truncate(responseText, 500));
                    return;
                }

                // 回填标题、正文，状态保持成功
                ArticleGenerationRecord update = new ArticleGenerationRecord();
                update.setId(recordId);
                update.setResponseTitle(title);
                update.setResponseContent(content);
                update.setStatus(1);
                articleGenerationRecordMapper.updateById(update);

            } catch (Exception e) {
                log.error("解析生成文章失败，recordId={}, exceptionType={}",
                        recordId, e.getClass().getSimpleName());
                updateRecordFailed(recordId, "解析结果异常：" + e.getMessage());
            }
        });
    }

    private String buildPrompt(String userMessage, List<KnowledgeSearchHit> hits, KnowledgeAgentScope scope) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[系统提示词]\n你是一位智能健康宣传文章助手，专门用于根据医生的知识库生成与他本人经历有关的科普文章，" +
                "生成的文章需要符合发布到公共媒体平台的要求，内容必须根据医生知识库进行编写的，不得虚假捏造。" +
                "返回格式必须是严格 JSON，字段为：" +
                "{\"title\":\"\",\"content\":\"\",\"sources\":[],\"citations\":[],\"medicalDisclaimer\":\"\",\"schemaJson\":{}}。" +
                "sources 只能填写知识库文件名或片段标题；citations 只能引用知识库来源，不得编造外部链接；" +
                "medicalDisclaimer 必须提示仅用于健康科普、不能替代医生面诊、诊断或治疗建议。");
        prompt.append("\n[当前智能体]\n");
        prompt.append(describeScope(scope));
        prompt.append("\n");
        prompt.append("[Content formatting rule]\n");
        prompt.append("The JSON content field must be publish-ready article body only. Do not put source filenames, .md/.pdf/.doc/.docx names, parenthetical source marks, or reference labels such as 参考文章/参考资料 in content. Put sources only in sources and citations.\n");
        prompt.append("[知识库检索结果]\n");

        if (hits.isEmpty()) {
            prompt.append("当前没有检索到可用的知识片段。\n");
        } else {
            int currentLength = 0;
            int maxContextChars = Math.max(2000, ragProperties.getMaxContextChars());
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeSearchHit hit = hits.get(i);
                String block = """
                        %d.
                        标题：%s
                        相似度：%.4f
                        内容：
                        %s
                        """.formatted(i + 1, StringUtils.hasText(hit.getTitle()) ? hit.getTitle() : "未知标题", hit.getScore(), StringUtils.hasText(hit.getText()) ? hit.getText() : "");
                if (currentLength + block.length() > maxContextChars) {
                    break;
                }
                prompt.append(block);
                currentLength += block.length();
            }
        }
        prompt.append("\n[用户问题]\n");
        prompt.append(userMessage);
        return prompt.toString();
    }

    private String describeScope(KnowledgeAgentScope scope) {
        if (scope == null) {
            return "未指定智能体";
        }
        String typeName = switch (scope.getAgentType()) {
            case KnowledgeAgentTypeConstants.HOSPITAL -> "医院智能体";
            case KnowledgeAgentTypeConstants.DEPARTMENT -> "科室智能体";
            case KnowledgeAgentTypeConstants.DOCTOR -> "医生智能体";
            default -> "未知智能体";
        };
        return "%s：医院=%s，科室=%s，医生=%s".formatted(
                typeName,
                nullToEmpty(scope.getHospitalName()),
                nullToEmpty(scope.getDepartmentName()),
                nullToEmpty(scope.getDoctorName())
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void updateRecordFailed(Long recordId, String error) {
        ArticleGenerationRecord update = new ArticleGenerationRecord();
        update.setId(recordId);
        update.setStatus(2);
        update.setErrorMessage(truncate(error, 1000));
        articleGenerationRecordMapper.updateById(update);
    }

    private String normalizeJson(String responseText) {
        if (responseText == null) {
            return "";
        }
        String normalized = responseText.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        return normalized;
    }

    private String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
