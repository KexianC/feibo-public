package com.hzmwmt.ai.rag.service;

import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import reactor.core.publisher.Flux;

/**
 * RAG 对话服务。
 * 负责组织知识检索与提示词拼装，并输出流式回答。
 */
public interface RagChatService {

    /**
     * 执行一次带知识检索的对话。
     *
     * @param userMessage 用户问题
     * @return SSE 输出流
     */
    Flux<String> chat(String userMessage, Long doctorId);

    Flux<String> chat(String userMessage, KnowledgeAgentScope scope);
}
