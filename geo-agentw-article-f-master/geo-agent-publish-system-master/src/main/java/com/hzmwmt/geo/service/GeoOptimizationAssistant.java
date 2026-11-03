package com.hzmwmt.geo.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface GeoOptimizationAssistant {

    @SystemMessage("""
            你是医疗内容GEO优化助手。你的任务是根据AI搜索未命中的问题，生成可用于补充公开科普页面的内容。
            严格遵守：
            1. 不得给出个人诊疗建议，不得承诺疗效，不得推荐具体药品。
            2. 不得夸大医生、医院、治疗效果或使用绝对化宣传。
            3. 内容必须客观、可验证，并明确提示仅用于健康科普，不能替代线下诊疗。
            4. 返回内容必须是严格JSON，不能包含markdown代码块或额外说明。
            """)
    Flux<String> optimize(String userMessage);
}
