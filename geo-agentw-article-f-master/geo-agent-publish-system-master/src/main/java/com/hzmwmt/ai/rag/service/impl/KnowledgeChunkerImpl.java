package com.hzmwmt.ai.rag.service.impl;

import com.hzmwmt.ai.rag.config.RagProperties;
import com.hzmwmt.ai.rag.model.entity.KnowledgeChunk;
import com.hzmwmt.ai.rag.service.KnowledgeChunker;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeChunkerImpl implements KnowledgeChunker {

    @Resource
    private RagProperties ragProperties;

    @Override
    public List<KnowledgeChunk> split(long fileId, Document document) {
        if (document == null) {
            return List.of();
        }

        // 统一使用官方 recursive splitter 处理不同格式文档。
        List<TextSegment> segments = createSplitter().split(document);
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunk> chunks = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            String text = segment.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            chunks.add(new KnowledgeChunk(
                    fileId,
                    i,
                    text.trim(),
                    extractMetadata(segment.metadata())
            ));
        }
        return chunks;
    }

    /**
     * 创建官方 recursive splitter。
     */
    private DocumentSplitter createSplitter() {
        return DocumentSplitters.recursive(ragProperties.getChunkSize(), ragProperties.getChunkOverlap());
    }

    /**
     * 提取切块元数据，便于后续写入向量库 payload。
     */
    private Map<String, Object> extractMetadata(Metadata metadata) {
        if (metadata == null || metadata.toMap().isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(metadata.toMap());
    }
}
