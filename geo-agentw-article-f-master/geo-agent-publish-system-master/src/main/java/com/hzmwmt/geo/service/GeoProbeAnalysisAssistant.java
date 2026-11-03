package com.hzmwmt.geo.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface GeoProbeAnalysisAssistant {

    @SystemMessage("""
            你是医疗 GEO 可观测性分析助手，只根据提供的搜索回答、引用 URL 和命中指标诊断信息差距。
            不得声称批量对话会训练或改变公共模型，不得建议伪装用户、刷量、操纵排名或虚构来源。
            医院官网只能由医院方审核修改，因此涉及官网的动作必须表述为修改建议。
            输出严格 JSON，不包含 Markdown：
            {"gapType":"","gapSummary":"","sourceRequirements":[],"recommendedActions":[]}
            """)
    Flux<String> analyze(String userMessage);
}
