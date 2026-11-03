package com.hzmwmt.ai.rag.document;

import com.hzmwmt.constant.DocumentExtensionConstants;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Markdown 文档解析策略
 * 当前版本先按纯文本处理，保留标题、列表等原始结构
 */
@Component
public class MarkdownDocumentParseStrategy extends AbstractDocumentParseStrategy {

    /**
     * Markdown 在当前版本按纯文本解析
     */
    private final DocumentParser documentParser = new TextDocumentParser();

    @Override
    public Set<String> supportedExtensions() {
        return DocumentExtensionConstants.MARKDOWN_EXTENSIONS;
    }

    @Override
    protected DocumentParser documentParser() {
        return documentParser;
    }
}
