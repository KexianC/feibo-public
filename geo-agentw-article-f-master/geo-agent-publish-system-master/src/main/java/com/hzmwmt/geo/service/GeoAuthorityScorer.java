package com.hzmwmt.geo.service;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Service
public class GeoAuthorityScorer {

    private static final Set<String> PUBLISHING_DOMAINS = Set.of(
            "baijiahao.baidu.com", "sohu.com", "163.com", "toutiao.com", "weibo.com", "zhihu.com"
    );

    @Resource
    private GeoOfficialDomainRegistry officialDomainRegistry;

    public Classification classify(String host, String url) {
        String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        String normalizedUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        boolean configuredOfficial = officialDomainRegistry.isOfficialHost(normalizedHost);
        if (normalizedHost.endsWith(".gov.cn") || "gov.cn".equals(normalizedHost)) {
            return new Classification("government", configuredOfficial, 95, "优先作为公共事实和政策依据");
        }
        if (normalizedHost.endsWith(".edu.cn") || normalizedHost.contains("hospital.")) {
            return new Classification("academic", configuredOfficial, 85, "可用于学术背景、诊疗规范和机构资质佐证");
        }
        if (configuredOfficial) {
            return new Classification("official_site", true, 90, "作为医院、科室和医生事实的首要引用源");
        }
        if ("mp.weixin.qq.com".equals(normalizedHost) || normalizedHost.endsWith("weixin.qq.com")) {
            return new Classification("official_social", false, 70, "核验账号主体后作为时效性补充来源");
        }
        if (PUBLISHING_DOMAINS.stream().anyMatch(normalizedHost::endsWith)) {
            return new Classification("publishing_platform", false, 45, "用于扩大可发现性，不作为核心医疗事实的唯一来源");
        }
        return new Classification("third_party", false, 55, "需核验作者、发布时间和原始出处后再使用");
    }

    public record Classification(String sourceType, boolean official, int authorityScore, String recommendation) {
    }
}
