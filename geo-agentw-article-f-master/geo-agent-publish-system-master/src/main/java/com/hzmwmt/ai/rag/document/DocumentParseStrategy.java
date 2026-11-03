package com.hzmwmt.ai.rag.document;

import dev.langchain4j.data.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * 文档解析策略
 * 按文件后缀选择不同的 LangChain4j 解析器
 */
public interface DocumentParseStrategy {

    /**
     * 当前策略支持的文件后缀
     *
     * @return 后缀集合，不包含点号
     */
    Set<String> supportedExtensions();

    /**
     * 解析上传文件
     *
     * @param file 上传文件
     * @return 解析后的文档
     * @throws IOException 读取文件失败
     */
    Document parse(MultipartFile file) throws IOException;

    /**
     * 判断是否支持指定后缀
     *
     * @param extension 文件后缀
     * @return 是否支持
     */
    default boolean supports(String extension) {
        return extension != null && supportedExtensions().contains(extension.toLowerCase());
    }
}
