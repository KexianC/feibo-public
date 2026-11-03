package com.hzmwmt.ai.rag.document;

import com.hzmwmt.constant.DocumentExtensionConstants;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Office 文档解析策略
 * 统一处理 Word、Excel、PPT 等格式
 */
@Component
public class OfficeDocumentParseStrategy extends AbstractDocumentParseStrategy {

    /**
     * Tika 解析器
     */
    private final DocumentParser documentParser = new ApacheTikaDocumentParser();

    @Override
    public Set<String> supportedExtensions() {
        return DocumentExtensionConstants.OFFICE_EXTENSIONS;
    }

    @Override
    protected DocumentParser documentParser() {
        return documentParser;
    }
}
