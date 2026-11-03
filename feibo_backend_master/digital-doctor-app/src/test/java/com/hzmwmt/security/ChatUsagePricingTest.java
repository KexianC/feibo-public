package com.hzmwmt.security;

import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;
import com.hzmwmt.domain.ai.service.chat.ChatPricing;
import com.hzmwmt.infrastructure.gateway.ai.openai.ChatUsageCapture;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ChatUsagePricingTest {
    @Test void nativePlatformsUseTheirOwnPricesAndMissingCacheStaysUnknown() {
        var kimi=record("kimik3","kimi-k3",1000,100,200L,"2026-09-08T04:00:00Z");
        ChatPricing.price(kimi,"api.moonshot.cn");
        assertThat(kimi.getEstimatedCost()).isEqualByComparingTo("0.0264");
        var baichuan=record("baichuan","Baichuan-M3-Plus",1000,100,null,"2026-09-08T04:00:00Z");
        ChatPricing.price(baichuan,"api.baichuan-ai.com");
        assertThat(baichuan.getEstimatedCost()).isEqualByComparingTo("0.0059");
        assertThat(baichuan.getCachedTokens()).isNull();
        var qianfan=record("qianfan","ernie-5.1",32768,100,null,"2026-09-08T04:00:00Z");
        ChatPricing.price(qianfan,"qianfan.baidubce.com");
        assertThat(qianfan.getInputPrice()).isEqualByComparingTo("4");
        qianfan.setInputTokens(32769L);
        ChatPricing.price(qianfan,"qianfan.baidubce.com");
        assertThat(qianfan.getInputPrice()).isEqualByComparingTo("6");
        ChatPricing.price(qianfan,"api.deepseek.com");
        assertThat(qianfan.getEstimatedCost()).isNull();
    }
    private ChatUsageRecord record(String provider, String model, long input, long output, Long cache, String instant) {
        ChatUsageRecord u = new ChatUsageRecord();
        u.setProvider(provider); u.setModel(model); u.setInputTokens(input); u.setOutputTokens(output);
        u.setCachedTokens(cache); u.setTotalTokens(input + output); u.setUsageSource("PROVIDER"); u.setRequestedAt(instant);
        return u;
    }
    @Test void peakAndWeekendUseRequestTimeAndDoNotDoubleCountCache() {
        var u = record("deepseek", "deepseek-v4-flash", 1000000, 100000, 200000L, "2026-09-08T01:00:00Z");
        ChatPricing.price(u, "api.deepseek.com");
        assertThat(u.getEstimatedCost()).isEqualByComparingTo("3.32");
        u.setRequestedAt("2026-09-12T01:00:00Z");
        ChatPricing.price(u, "api.deepseek.com");
        assertThat(u.getEstimatedCost()).isEqualByComparingTo("1.66");
        u.setRequestedAt("2026-09-08T04:00:00Z");
        ChatPricing.price(u, "api.deepseek.com");
        assertThat(u.getEstimatedCost()).isEqualByComparingTo("1.66");
    }
    @Test void qwenTierBoundaryAndUnknownMaxCache() {
        var u = record("qwen", "qwen3.7-flash", 32768, 1000, 0L, "2026-09-08T01:00:00Z");
        ChatPricing.price(u, "dashscope.aliyuncs.com");
        assertThat(u.getInputPrice()).isEqualByComparingTo("0.2");
        u.setInputTokens(32769L);
        ChatPricing.price(u, "dashscope.aliyuncs.com");
        assertThat(u.getInputPrice()).isEqualByComparingTo("0.6");
        u.setModel("qwen3.8-max");
        u.setCachedTokens(1L);
        ChatPricing.price(u, "dashscope.aliyuncs.com");
        assertThat(u.getEstimatedCost()).isNull();
        u.setCachedTokens(0L);
        ChatPricing.price(u, "dashscope.aliyuncs.com");
        assertThat(u.getEstimatedCost()).isNotNull();
    }
    @Test void unknownPriceOrMissingCountersAreNeverFree() {
        var u = record("deepseek", "deepseek-v4-flash", 100, 50, null, "2026-09-08T01:00:00Z");
        ChatPricing.price(u, "api.deepseek.com");
        assertThat(u.getCostStatus()).isEqualTo("CACHE_NOT_REPORTED");
        assertThat(u.getEstimatedCost()).isNull();
        u.setCachedTokens(0L);
        ChatPricing.price(u, "third-party.example");
        assertThat(u.getEstimatedCost()).isNull();
        assertThat(u.getCostStatus()).isEqualTo("PRICE_UNCONFIRMED");
        u.setModel("unknown-version");
        ChatPricing.price(u, "api.deepseek.com");
        assertThat(u.getEstimatedCost()).isNull();
    }
    @Test void repeatedStreamUsageIsNotAddedAndEmptyFinalChunkDoesNotEraseIt() {
        var u = new ChatUsageRecord();
        var nativeUsage = new OpenAiApi.Usage(50, 100, 150,
                new OpenAiApi.Usage.PromptTokensDetails(null, 20), null, null, null);
        var response = new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(100, 50, 150, nativeUsage)).build());
        ChatUsageCapture.capture(u, response);
        ChatUsageCapture.capture(u, response);
        ChatUsageCapture.capture(u, new ChatResponse(List.of()));
        assertThat(u.getTotalTokens()).isEqualTo(150);
        assertThat(u.getCachedTokens()).isEqualTo(20);
        assertThat(u.getUsageSource()).isEqualTo("PROVIDER");
    }
    @Test void syntheticUsageNeverBecomesMeasured() {
        var u = new ChatUsageRecord();
        ChatUsageCapture.capture(u, new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(20, 30)).build()));
        assertThat(u.getUsageSource()).isEqualTo("NOT_REPORTED");
        assertThat(u.getTotalTokens()).isNull();
    }
}
