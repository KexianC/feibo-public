package com.hzmwmt.ai.rag.document;

import com.hzmwmt.constant.DocumentExtensionConstants;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * PDF 文档解析策略
 */
@Component
public class PdfDocumentParseStrategy extends AbstractDocumentParseStrategy {

    /**
     * PDF 解析器
     */
    private final DocumentParser documentParser = new ApachePdfBoxDocumentParser();

    @Override
    public Set<String> supportedExtensions() {
        return DocumentExtensionConstants.PDF_EXTENSIONS;
    }

    @Override
    protected DocumentParser documentParser() {
        return documentParser;
    }
}
