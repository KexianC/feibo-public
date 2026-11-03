package com.hzmwmt.domain.ai.service.chat;

import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;

/** Official public text-inference rates, CNY per million tokens, verified 2026-09-08.
 * Unknown model/endpoint/cache counters deliberately remain unpriced.
 * This estimates API usage, not wallet deductions or customer quota billing.
 */
public final class ChatPricing {
    private ChatPricing() {}
    public static void price(ChatUsageRecord u, String endpointHost) {
        u.setEstimatedCost(null);
        if (!"PROVIDER".equals(u.getUsageSource())) return;
        u.setCostStatus("PRICE_UNCONFIRMED");
        long input = u.getInputTokens();
        String model = u.getModel();
        String provider = u.getProvider();
        if ("deepseek".equals(provider) && "api.deepseek.com".equals(endpointHost)
                && ("deepseek-v4-flash".equals(model) || "deepseek-v4-pro".equals(model))) {
            ZonedDateTime time = Instant.parse(u.getRequestedAt()).atZone(ZoneId.of("Asia/Shanghai"));
            int hour = time.getHour();
            boolean peak = time.getDayOfWeek().getValue() <= 5 && ((hour >= 9 && hour < 12) || (hour >= 14 && hour < 18));
            boolean pro = model.endsWith("pro");
            rates(u, pro ? "9" : "3", pro ? "27" : "9", pro ? "0.3" : "0.1",
                    "https://api-docs.deepseek.com/zh-cn/quick_start/pricing/", peak ? "工作日高峰时段" : "空闲时段五折");
            if (!peak) {
                u.setInputPrice(u.getInputPrice().divide(BigDecimal.valueOf(2)));
                u.setOutputPrice(u.getOutputPrice().divide(BigDecimal.valueOf(2)));
                u.setCachePrice(u.getCachePrice().divide(BigDecimal.valueOf(2)));
            }
        } else if ("qwen".equals(provider) && "dashscope.aliyuncs.com".equals(endpointHost)) {
            if (input > 1048576) return;
            String source = "https://help.aliyun.com/zh/model-studio/model-pricing";
            if ("qwen3.8-max".equals(model)) rates(u, "12", "36", null, source, "北京地域；缓存单价需在百炼控制台确认，存在缓存命中时暂不计价");
            else if ("qwen3.7-plus".equals(model)) {
                boolean low = input <= 262144;
                rates(u, low ? "2" : "6", low ? "8" : "24", low ? "0.4" : "1.2", source,
                        "北京地域；公开原价，未抵扣限时折扣或赠送额度；256K输入阶梯");
            } else if ("qwen3.7-flash".equals(model)) {
                rates(u, input <= 32768 ? "0.2" : input <= 262144 ? "0.6" : "1.2",
                        input <= 32768 ? "0.8" : input <= 262144 ? "2.4" : "4.8",
                        input <= 32768 ? "0.04" : input <= 262144 ? "0.12" : "0.24", source, "北京地域；32K/256K输入阶梯；隐式缓存20%");
            } else return;
        } else if ("doubao".equals(provider) && "ark.cn-beijing.volces.com".equals(endpointHost)
                && "doubao-seed-2-1-pro-260628".equals(model)) {
            rates(u, "6", "30", "1.2", "https://www.volcengine.com/product/doubao", "文本实时推理；不含缓存存储及其他服务费");
        } else if ("qianfan".equals(provider) && "qianfan.baidubce.com".equals(endpointHost)
                && ("ernie-5.1".equals(model) || "ernie-5.0".equals(model))) {
            if (input > 131072) return;
            boolean low = input <= 32768;
            boolean newer = "ernie-5.1".equals(model);
            String in = newer ? (low ? "4" : "6") : (low ? "6" : "10");
            rates(u, in, newer ? (low ? "18" : "22") : (low ? "24" : "40"), in,
                    "https://cloud.baidu.com/doc/qianfan/s/wmh4sv6ya", "千帆在线按量公开价；32K输入阶梯；不含资源包及工具服务费用");
        } else if ("baichuan".equals(provider) && "api.baichuan-ai.com".equals(endpointHost) && "Baichuan-M3-Plus".equals(model)) {
            rates(u, "5", "9", "5", "https://platform.baichuan-ai.com/prices", "仅Token推理费；不含平台自动触发的医疗搜索等额外费用");
        } else if ("kimik3".equals(provider) && "api.moonshot.cn".equals(endpointHost) && "kimi-k3".equals(model)) {
            rates(u, "20", "100", "2", "https://platform.kimi.com/docs/pricing/chat-k3", "Kimi国内平台公开目录价；不含工具调用服务费");
        } else if ("hy3".equals(provider) && "tokenhub.tencentmaas.com".equals(endpointHost) && "hy3".equals(model)) {
            rates(u, "1", "4", "0.25", "https://cloud.tencent.com/document/product/1823/130055", "腾讯云TokenHub公开目录价；文本实时推理");
        } else return;
        boolean uniformInputPrice = u.getCachePrice() != null && u.getCachePrice().compareTo(u.getInputPrice()) == 0;
        if (u.getCachedTokens() == null && !uniformInputPrice) {
            u.setCostStatus("CACHE_NOT_REPORTED");
            return;
        }
        long cached = u.getCachedTokens() == null ? 0 : u.getCachedTokens();
        if (cached > 0 && u.getCachePrice() == null) {
            u.setCostStatus("PRICE_UNCONFIRMED");
            return;
        }
        if (cached < 0 || cached > input || input < 0 || u.getOutputTokens() < 0) {
            u.setCostStatus("INVALID_USAGE");
            return;
        }
        BigDecimal cost = u.getInputPrice().multiply(BigDecimal.valueOf(input - cached))
                .add(cached == 0 ? BigDecimal.ZERO : u.getCachePrice().multiply(BigDecimal.valueOf(cached)))
                .add(u.getOutputPrice().multiply(BigDecimal.valueOf(u.getOutputTokens())))
                .divide(BigDecimal.valueOf(1000000), 10, RoundingMode.HALF_UP);
        u.setEstimatedCost(cost);
        u.setCostStatus("ESTIMATED");
    }
    private static void rates(ChatUsageRecord u, String input, String output, String cache, String source, String note) {
        u.setInputPrice(new BigDecimal(input));
        u.setOutputPrice(new BigDecimal(output));
        u.setCachePrice(cache == null ? null : new BigDecimal(cache));
        u.setPriceVersion("official-2026-09-08-v1");
        u.setPriceSource(source);
        u.setPriceNote(note);
    }
}
