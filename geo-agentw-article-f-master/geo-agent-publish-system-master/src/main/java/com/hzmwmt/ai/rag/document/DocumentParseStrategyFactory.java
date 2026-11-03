package com.hzmwmt.ai.rag.document;

import com.hzmwmt.constant.DocumentExtensionConstants;
import com.hzmwmt.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 文档解析策略工厂
 * 根据文件后缀选择合适的解析策略
 */
@Component
@Slf4j
public class DocumentParseStrategyFactory {

    /**
     * 已注册的解析策略列表
     */
    private final List<DocumentParseStrategy> strategies;

    public DocumentParseStrategyFactory(List<DocumentParseStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 根据文件名选择解析策略
     *
     * @param filename 文件名
     * @return 匹配到的策略
     */
    public DocumentParseStrategy getStrategy(String filename) {
        String extension = resolveExtension(filename);
        for (DocumentParseStrategy strategy : strategies) {
            if (strategy.supports(extension)) {
                log.info("选择文档解析策略，fileNameLength={}, extension={}, strategy={}",
                        filename.length(), extension, strategy.getClass().getSimpleName());
                return strategy;
            }
        }
        throw new AppException("UNSUPPORTED_FILE", "当前仅支持 " + DocumentExtensionConstants.SUPPORTED_EXTENSIONS_TEXT + " 等文件");
    }

    /**
     * 返回当前系统支持的全部后缀
     *
     * @return 已支持的后缀集合
     */
    public Set<String> supportedExtensions() {
        Set<String> extensions = new TreeSet<>(Comparator.naturalOrder());
        for (DocumentParseStrategy strategy : strategies) {
            extensions.addAll(strategy.supportedExtensions());
        }
        return extensions;
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new AppException("UNSUPPORTED_FILE", "当前仅支持 " + DocumentExtensionConstants.SUPPORTED_EXTENSIONS_TEXT + " 等文件");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
