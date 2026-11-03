package com.hzmwmt.ai.rag.controller;

import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.service.KnowledgeAgentScopeResolver;
import com.hzmwmt.ai.rag.service.RagChatService;
import com.hzmwmt.common.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话接口。
 */
@RestController
@RequestMapping
public class AssistantController {

    @Resource
    private RagChatService ragChatService;

    @Resource
    private KnowledgeAgentScopeResolver scopeResolver;

    /**
     * 流式问答接口。
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestParam("userMessage") String userMessage,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId) {
        if (!StringUtils.hasText(userMessage)) {
            throw new AppException("PARAM_INVALID", "用户消息不能为空");
        }

        KnowledgeAgentScope scope = scopeResolver.resolve(agentType, hospitalId, departmentId, doctorId);
        return ragChatService.chat(userMessage.trim(), scope);
    }
}
