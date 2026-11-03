package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.domain.ai.model.entity.AIContentReviewResultEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIPortContentReviewPolicyTest {

    @Test
    void passesWhenThereAreOnlySuggestionsEvenIfModelPassFlagIsFalse() {
        String response = """
                {
                  "typos": ["建议优化一处用词"],
                  "scientificIssues": ["建议补充数据来源"],
                  "principleErrors": [],
                  "suggestion": "优化表达并补充引用",
                  "pass": false
                }
                """;

        AIContentReviewResultEntity result = AIPort.parseContentReviewResult(response, List.of("提示词"));

        assertThat(result.getPass()).isTrue();
        assertThat(result.getTypos()).containsExactly("建议优化一处用词");
        assertThat(result.getScientificIssues()).containsExactly("建议补充数据来源");
        assertThat(result.getSensitiveWords()).containsExactly("提示词");
    }

    @Test
    void rejectsOnlyWhenConcretePrincipleErrorsExist() {
        String response = """
                {
                  "typos": [],
                  "scientificIssues": [],
                  "principleErrors": ["建议患者立即停用医生开具的必要药物"],
                  "suggestion": "改为咨询医生后调整用药",
                  "pass": true
                }
                """;

        AIContentReviewResultEntity result = AIPort.parseContentReviewResult(response, List.of());

        assertThat(result.getPass()).isFalse();
        assertThat(result.getScientificIssues())
                .containsExactly("原则性错误：建议患者立即停用医生开具的必要药物");
    }

    @Test
    void retriesWhenPrincipleErrorClassificationIsMissing() {
        String response = """
                {
                  "typos": [],
                  "scientificIssues": [],
                  "suggestion": "无",
                  "pass": true
                }
                """;

        assertThatThrownBy(() -> AIPort.parseContentReviewResult(response, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI内容审核结果无效");
    }
}
