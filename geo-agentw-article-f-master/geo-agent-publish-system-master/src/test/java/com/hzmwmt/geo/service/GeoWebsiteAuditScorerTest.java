package com.hzmwmt.geo.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeoWebsiteAuditScorerTest {

    private final GeoWebsiteAuditScorer scorer = new GeoWebsiteAuditScorer();

    @Test
    void awardsFullScoreToCompleteIndexableMedicalWebsite() {
        GeoWebsiteAuditScorer.AuditResult result = scorer.score(
                html(qualityPage(""), Map.of()),
                text("https://hospital.example/robots.txt", "User-agent: *\nAllow: /\n"),
                text("https://hospital.example/sitemap.xml", "<?xml version=\"1.0\"?><urlset></urlset>"));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.pageTitle()).isEqualTo("杭城示例中医院官方网站");
        assertThat(result.recommendations()).isEmpty();
        assertThat(result.checks()).hasSize(14).allSatisfy(check ->
                assertThat(check.get("passed")).isEqualTo(true));
        assertThat(result.contentHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void removesIndexabilityPointsWhenPageAndRobotsBlockCrawling() {
        GeoWebsiteAuditScorer.AuditResult result = scorer.score(
                html(qualityPage("<meta name=\"robots\" content=\"noindex,nofollow\">"),
                        Map.of("x-robots-tag", "noindex")),
                text("https://hospital.example/robots.txt", "User-agent: *\nDisallow: /\n"),
                text("https://hospital.example/sitemap.xml", "<urlset></urlset>"));

        assertThat(result.score()).isEqualTo(90);
        assertThat(result.checks()).filteredOn(check -> "indexable".equals(check.get("key")))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.get("passed")).isEqualTo(false);
                    assertThat(check.get("weight")).isEqualTo(10);
                    assertThat(check.get("detail")).asString().contains("noindex");
                });
        assertThat(result.recommendations())
                .anyMatch(value -> value.contains("noindex") && value.contains("robots.txt"));
    }

    @Test
    void honorsCaseInsensitiveXRobotsTagHeader() {
        GeoWebsiteAuditScorer.AuditResult result = scorer.score(
                html(qualityPage(""), Map.of("X-Robots-Tag", "max-snippet:-1, NOINDEX")),
                text("https://hospital.example/robots.txt", "User-agent: *\nAllow: /\n"),
                text("https://hospital.example/sitemap.xml", "<urlset></urlset>"));

        assertThat(result.score()).isEqualTo(90);
        assertThat(result.checks()).filteredOn(check -> "indexable".equals(check.get("key")))
                .extracting(check -> check.get("passed"))
                .containsExactly(false);
    }

    private static String qualityPage(String extraHead) {
        String description = "杭城示例中医院提供中医内科、针灸、康复与专家门诊服务，公开介绍医疗资质、就诊流程、出诊信息及联系方式。";
        String body = "医院概况、特色科室、专家团队、诊疗服务与就医指南。".repeat(55);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>杭城示例中医院官方网站</title>
                  <meta name="description" content="%s">
                  <link rel="canonical" href="https://hospital.example/">
                  %s
                  <script type="application/ld+json">
                    {"@context":"https://schema.org","@type":"Hospital","name":"杭城示例中医院","dateModified":"2026-09-02"}
                  </script>
                </head>
                <body>
                  <h1>杭城示例中医院</h1>
                  <section><h2>常见问题 FAQ</h2><p>如何预约门诊？请通过官方渠道预约。</p></section>
                  <time datetime="2026-09-02">更新时间：2026-09-02</time>
                  <main>%s</main>
                </body>
                </html>
                """.formatted(description, extraHead, body);
    }

    private static GeoWebsiteHttpFetcher.FetchResult html(String body, Map<String, String> headers) {
        return response("https://hospital.example/", 200, "text/html; charset=utf-8", body, headers);
    }

    private static GeoWebsiteHttpFetcher.FetchResult text(String url, String body) {
        return response(url, 200, "text/plain; charset=utf-8", body, Map.of());
    }

    private static GeoWebsiteHttpFetcher.FetchResult response(
            String url,
            int status,
            String contentType,
            String body,
            Map<String, String> headers) {
        return new GeoWebsiteHttpFetcher.FetchResult(
                url,
                url,
                status,
                contentType,
                headers,
                body.getBytes(StandardCharsets.UTF_8),
                12L);
    }
}
