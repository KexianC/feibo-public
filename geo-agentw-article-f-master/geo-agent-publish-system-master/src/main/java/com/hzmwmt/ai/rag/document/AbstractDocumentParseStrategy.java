package com.hzmwmt.ai.rag.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析策略基类
 */
public abstract class AbstractDocumentParseStrategy implements DocumentParseStrategy {

    /**
     * 返回当前策略实际使用的解析器
     *
     * @return LangChain4j 解析器
     */
    protected abstract DocumentParser documentParser();

    @Override
    public Document parse(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return documentParser().parse(inputStream);
        }
    }
}
