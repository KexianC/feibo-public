package com.hzmwmt.domain.service.user.impl;

import com.alibaba.fastjson.JSON;
import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;
import com.hzmwmt.domain.entity.user.TAiMessage;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationUsageSummaryTest {
    private TAiMessage message(long id, boolean assistant, String snapshot) {
        TAiMessage m = new TAiMessage(); m.setAiMessageId(id); m.setAiModelConfigId(9005300000000000001L);
        m.setRole(assistant); m.setToken(10L); m.setUsageJson(snapshot); return m;
    }
    @Test void legacyEstimatesAreNotCountedAsMeasuredOrFreeAndSnapshotSurvivesRename() {
        ChatUsageRecord usage = new ChatUsageRecord(); usage.setModel("original-model-version");
        usage.setUsageSource("PROVIDER"); usage.setTotalTokens(1000L);
        usage.setCostStatus("ESTIMATED"); usage.setEstimatedCost(new BigDecimal("0.0012345678"));
        var result = AdminConversationServiceImpl.summarize(List.of(
                message(1, false, null), message(2, true, null),
                message(3, true, JSON.toJSONString(usage)), message(4,true,"invalid-json")),
                Map.of(9005300000000000001L, "renamed-model"));
        assertThat(result.getTotalReplies()).isEqualTo(3);
        assertThat(result.getMeteredReplies()).isEqualTo(1);
        assertThat(result.getPricedReplies()).isEqualTo(1);
        assertThat(result.getTokens()).isEqualTo(1000L);
        assertThat(result.getEstimatedCost()).isEqualByComparingTo("0.0012345678");
        assertThat(result.getMessages().get(2).getModel()).isEqualTo("original-model-version");
        assertThat(result.getMessages().get(2).getModelId()).isEqualTo("9005300000000000001");
    }
    @Test void unmeteredHistoryHasUnknownTotals() {
        var result = AdminConversationServiceImpl.summarize(List.of(message(1,true,null)), Map.of());
        assertThat(result.getEstimatedCost()).isNull();
        assertThat(result.getTokens()).isNull();
        assertThat(result.getMessages().get(0).getModel()).contains("未记录");
    }
}
