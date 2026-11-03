package com.hzmwmt.geo.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class GeoWebsiteAuditScorer {

    private static final Pattern UPDATED_SIGNAL = Pattern.compile(
            "(?i)(dateModified|article:modified_time|更新时间|更新日期|最后更新|发布时间|publish(?:ed)?[_ -]?time)");
    private static final Pattern FAQ_SIGNAL = Pattern.compile("(?i)(FAQPage|常见问题|问答|FAQ|Q&A)");
    private static final Pattern MEDICAL_SCHEMA = Pattern.compile(
            "(?i)(Hospital|MedicalOrganization|MedicalWebPage|Physician|MedicalClinic|FAQPage)");

    public AuditResult score(
            GeoWebsiteHttpFetcher.FetchResult homepage,
            GeoWebsiteHttpFetcher.FetchResult robots,
            GeoWebsiteHttpFetcher.FetchResult sitemap) {
        Document document = parse(homepage);
        String title = document.title() == null ? "" : document.title().trim();
        String description = metaContent(document, "description");
        String robotsMeta = metaContent(document, "robots").toLowerCase(Locale.ROOT);
        String canonical = document.select("link[rel=canonical]").stream()
                .map(element -> element.attr("abs:href"))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        int h1Count = document.select("h1").size();
        boolean viewport = document.selectFirst("meta[name=viewport]") != null;
        List<Element> jsonLdElements = document.select("script[type=application/ld+json]");
        String jsonLdText = jsonLdElements.stream()
                .map(Element::data)
                .reduce("", (left, right) -> left + "\n" + right);
        boolean medicalSchema = MEDICAL_SCHEMA.matcher(jsonLdText).find();

        Document visibleDocument = document.clone();
        visibleDocument.select("script,style,noscript,svg,template").remove();
        int textLength = visibleDocument.body() == null ? 0 : visibleDocument.body().text().trim().length();
        String combinedMarkup = document.html();
        boolean faqSignal = FAQ_SIGNAL.matcher(combinedMarkup).find();
        boolean updatedSignal = UPDATED_SIGNAL.matcher(combinedMarkup).find()
                || document.selectFirst("time[datetime]") != null;

        String robotsText = bodyText(robots);
        boolean robotsReachable = successful(robots);
        boolean disallowAll = robotsReachable && disallowsAll(robotsText);
        boolean noIndex = robotsMeta.contains("noindex") || hasHeaderNoIndex(homepage);
        boolean sitemapReachable = successful(sitemap);

        List<Map<String, Object>> checks = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        addCheck(checks, recommendations, "reachable", "页面可访问", homepage.status() >= 200 && homepage.status() < 300,
                15, "HTTP " + homepage.status(), "确保官网首页稳定返回 2xx 状态码");
        addCheck(checks, recommendations, "https", "HTTPS", homepage.finalUrl().startsWith("https://"),
                8, homepage.finalUrl().startsWith("https://") ? "已启用 HTTPS" : "当前为 HTTP",
                "启用 HTTPS，并将 HTTP 永久重定向到 HTTPS");
        addCheck(checks, recommendations, "indexable", "搜索引擎可索引", !noIndex && !disallowAll,
                10, noIndex ? "页面声明 noindex" : disallowAll ? "robots.txt 禁止全站抓取" : "未发现全站索引阻断",
                "移除 noindex 或 robots.txt 中对全站的 Disallow: /（确认业务允许后）");
        addCheck(checks, recommendations, "title", "页面标题", title.length() >= 8 && title.length() <= 80,
                8, StringUtils.hasText(title) ? "标题长度 " + title.length() : "未设置 title",
                "设置清晰且唯一的页面标题，建议包含机构名称与核心服务");
        addCheck(checks, recommendations, "description", "页面摘要", description.length() >= 40 && description.length() <= 200,
                8, StringUtils.hasText(description) ? "摘要长度 " + description.length() : "未设置 description",
                "补充 40–200 字的 meta description，直接说明机构资质、特色科室和服务范围");
        addCheck(checks, recommendations, "canonical", "规范网址", StringUtils.hasText(canonical),
                7, StringUtils.hasText(canonical) ? canonical : "未设置 canonical",
                "为页面设置指向最终公开网址的 canonical 链接");
        addCheck(checks, recommendations, "h1", "主标题结构", h1Count == 1,
                6, "H1 数量 " + h1Count,
                "每个页面保留一个明确的 H1 主标题");
        addCheck(checks, recommendations, "content", "可提取正文", textLength >= 800,
                10, "可见正文约 " + textLength + " 字",
                "增加可直接提取的机构、科室、医生、诊疗特色和就医信息，避免只用图片呈现");
        addCheck(checks, recommendations, "viewport", "移动端适配", viewport,
                5, viewport ? "已设置 viewport" : "未设置 viewport",
                "增加 viewport 元信息并确保移动端内容完整可读");
        addCheck(checks, recommendations, "structuredData", "医疗结构化数据", medicalSchema,
                10, medicalSchema ? "发现医疗/FAQ JSON-LD" : "JSON-LD 块 " + jsonLdElements.size() + " 个，未发现医疗类型",
                "增加 Hospital、MedicalOrganization、MedicalWebPage、Physician 或 FAQPage JSON-LD");
        addCheck(checks, recommendations, "faq", "直接问答内容", faqSignal,
                5, faqSignal ? "发现 FAQ/问答信号" : "未发现 FAQ/问答信号",
                "围绕患者高频问题增加简洁、可核验的 FAQ，并标注适用边界");
        addCheck(checks, recommendations, "updated", "更新时间信号", updatedSignal,
                4, updatedSignal ? "发现发布时间或更新时间" : "未发现更新时间",
                "公开展示内容发布时间与最近更新时间");
        addCheck(checks, recommendations, "robots", "robots.txt", robotsReachable,
                2, robotsReachable ? "可访问" : "未发现或不可访问",
                "提供可访问的 robots.txt，并明确允许公开内容被检索");
        addCheck(checks, recommendations, "sitemap", "站点地图", sitemapReachable,
                2, sitemapReachable ? "可访问" : "未发现或不可访问",
                "提供 sitemap.xml 并保持重要页面链接有效");

        int totalScore = checks.stream()
                .mapToInt(check -> Boolean.TRUE.equals(check.get("passed")) ? (Integer) check.get("weight") : 0)
                .sum();
        return new AuditResult(
                Math.max(0, Math.min(100, totalScore)),
                title,
                checks,
                recommendations.stream().distinct().toList(),
                sha256(homepage.body()));
    }

    private Document parse(GeoWebsiteHttpFetcher.FetchResult homepage) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(homepage.body()), null, homepage.finalUrl());
        } catch (Exception e) {
            return Jsoup.parse("", homepage.finalUrl());
        }
    }

    private String metaContent(Document document, String name) {
        Element meta = document.selectFirst("meta[name=" + name + "]");
        return meta == null ? "" : meta.attr("content").trim();
    }

    private boolean successful(GeoWebsiteHttpFetcher.FetchResult result) {
        return result != null && result.status() >= 200 && result.status() < 300;
    }

    private String bodyText(GeoWebsiteHttpFetcher.FetchResult result) {
        return result == null || result.body() == null
                ? ""
                : new String(result.body(), StandardCharsets.UTF_8);
    }

    private boolean disallowsAll(String robotsText) {
        if (!StringUtils.hasText(robotsText)) {
            return false;
        }
        boolean wildcardGroup = false;
        for (String rawLine : robotsText.split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String directive = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if ("user-agent".equals(directive)) {
                wildcardGroup = "*".equals(value);
            } else if (wildcardGroup && "disallow".equals(directive) && "/".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHeaderNoIndex(GeoWebsiteHttpFetcher.FetchResult homepage) {
        return homepage.headers().entrySet().stream()
                .filter(entry -> "x-robots-tag".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("noindex"));
    }

    private void addCheck(
            List<Map<String, Object>> checks,
            List<String> recommendations,
            String key,
            String label,
            boolean passed,
            int weight,
            String detail,
            String recommendation) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("label", label);
        check.put("passed", passed);
        check.put("weight", weight);
        check.put("detail", detail);
        checks.add(check);
        if (!passed && StringUtils.hasText(recommendation)) {
            recommendations.add(recommendation);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes));
        } catch (Exception e) {
            return "";
        }
    }

    public record AuditResult(
            int score,
            String pageTitle,
            List<Map<String, Object>> checks,
            List<String> recommendations,
            String contentHash) {
    }
}
