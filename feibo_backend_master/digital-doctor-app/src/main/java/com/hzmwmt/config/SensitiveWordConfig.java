package com.hzmwmt.config;

import com.github.houbb.sensitive.word.api.IWordAllow;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: 敏感词配置 - 针对医疗场景优化，从配置文件加载敏感词和白名单
 * @ClassName SensitiveWordConfig
 * @Date: 2025/9/23 12:29
 */
@Slf4j
@Configuration
public class SensitiveWordConfig {

    /**
     * 从classpath文件中加载词汇列表
     */
    private static List<String> loadWordsFromFile(String fileName) {
        List<String> words = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // 跳过空行和注释行
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        words.add(line);
                    }
                }
            }
            log.info("成功加载敏感词词汇: count={}", words.size());
        } catch (IOException e) {
            log.error("加载敏感词文件失败: type={}", e.getClass().getSimpleName());
        }
        return words;
    }

    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        return SensitiveWordBs.newInstance()
                // 使用自定义敏感词黑名单实现
                .wordDeny(new CustomWordDeny())
                // 使用医疗相关白名单实现
                .wordAllow(new CustomWordAllow())

                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(false)
                // 针对医疗场景优化：关闭可能误判的检查
                .enableNumCheck(false)   // 关闭数字检查，避免误判医疗数据
                .enableEmailCheck(false) // 关闭邮箱检查
                .enableUrlCheck(false)   // 关闭URL检查
                .enableWordCheck(true)   // 只保留词汇检查
                .numCheckLen(1024)
                .init();
    }

    /**
     * 自定义敏感词黑名单实现
     */
    private static class CustomWordDeny implements IWordDeny {
        @Override
        public List<String> deny() {
            return loadWordsFromFile("sensitive-words.txt");
        }
    }

    /**
     * 自定义白名单实现
     */
    private static class CustomWordAllow implements IWordAllow {
        @Override
        public List<String> allow() {
            return loadWordsFromFile("medical-whitelist.txt");
        }
    }
}

