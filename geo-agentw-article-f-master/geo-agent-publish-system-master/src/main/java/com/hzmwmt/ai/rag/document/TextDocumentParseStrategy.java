package com.hzmwmt.ai.rag.document;

import com.hzmwmt.constant.DocumentExtensionConstants;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 纯文本解析策略
 */
@Component
public class TextDocumentParseStrategy extends AbstractDocumentParseStrategy {

    /**
     * 文本解析器
     */
    private final DocumentParser documentParser = new TextDocumentParser();

    @Override
    public Set<String> supportedExtensions() {
        return DocumentExtensionConstants.TEXT_EXTENSIONS;
    }

    @Override
    protected DocumentParser documentParser() {
        return documentParser;
    }
}
