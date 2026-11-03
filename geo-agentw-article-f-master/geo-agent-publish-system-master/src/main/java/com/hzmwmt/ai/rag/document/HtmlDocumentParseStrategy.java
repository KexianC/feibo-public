package com.hzmwmt.ai.rag.document;

import com.hzmwmt.constant.DocumentExtensionConstants;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * HTML 文档解析策略
 */
@Component
public class HtmlDocumentParseStrategy extends AbstractDocumentParseStrategy {

    /**
     * 先按文本读取 HTML 源码
     */
    private final DocumentParser documentParser = new TextDocumentParser();

    /**
     * 再将 HTML 转换为纯文本
     */
    private final HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer();

    @Override
    public Set<String> supportedExtensions() {
        return DocumentExtensionConstants.HTML_EXTENSIONS;
    }

    @Override
    protected DocumentParser documentParser() {
        return documentParser;
    }

    @Override
    public Document parse(MultipartFile file) throws IOException {
        Document document = super.parse(file);
        return transformer.transform(document);
    }
}
