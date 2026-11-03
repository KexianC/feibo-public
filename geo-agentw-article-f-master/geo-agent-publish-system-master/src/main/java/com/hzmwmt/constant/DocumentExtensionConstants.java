package com.hzmwmt.constant;

import java.util.Set;

/**
 * 文档后缀常量。
 */
public interface DocumentExtensionConstants {

    String PDF = "pdf";
    String DOC = "doc";
    String DOCX = "docx";
    String XLS = "xls";
    String XLSX = "xlsx";
    String PPT = "ppt";
    String PPTX = "pptx";
    String HTML = "html";
    String MD = "md";
    String TXT = "txt";
    String CSV = "csv";

    Set<String> PDF_EXTENSIONS = Set.of(PDF);

    Set<String> OFFICE_EXTENSIONS = Set.of(DOC, DOCX, XLS, XLSX, PPT, PPTX);

    Set<String> HTML_EXTENSIONS = Set.of(HTML);

    Set<String> MARKDOWN_EXTENSIONS = Set.of(MD);

    Set<String> TEXT_EXTENSIONS = Set.of(TXT, CSV);

    Set<String> ALL_SUPPORTED_EXTENSIONS = Set.of(
            PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, HTML, MD, TXT, CSV
    );

    String SUPPORTED_EXTENSIONS_TEXT = "pdf、doc、docx、xls、xlsx、ppt、pptx、html、md、txt、csv";
}
