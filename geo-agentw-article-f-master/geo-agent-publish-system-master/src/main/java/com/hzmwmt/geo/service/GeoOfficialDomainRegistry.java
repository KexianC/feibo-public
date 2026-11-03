package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoWebsiteMonitorMapper;
import com.hzmwmt.geo.model.entity.GeoWebsiteMonitor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GeoOfficialDomainRegistry {

    private static final long CACHE_MILLIS = 30_000L;

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private GeoWebsiteMonitorMapper websiteMonitorMapper;

    private volatile DomainCache cache = new DomainCache(0, List.of());

    public List<String> officialDomains() {
        DomainCache current = cache;
        long now = System.currentTimeMillis();
        if (current.expiresAt() > now) {
            return current.domains();
        }
        synchronized (this) {
            current = cache;
            if (current.expiresAt() > now) {
                return current.domains();
            }
            Set<String> domains = new LinkedHashSet<>();
            if (geoProperties.getOfficialDomains() != null) {
                geoProperties.getOfficialDomains().stream()
                        .map(this::normalizeDomain)
                        .filter(StringUtils::hasText)
                        .forEach(domains::add);
            }
            websiteMonitorMapper.selectList(new LambdaQueryWrapper<GeoWebsiteMonitor>()
                            .eq(GeoWebsiteMonitor::getActive, 1))
                    .stream()
                    .map(GeoWebsiteMonitor::getDomain)
                    .map(this::normalizeDomain)
                    .filter(StringUtils::hasText)
                    .forEach(domains::add);
            DomainCache refreshed = new DomainCache(now + CACHE_MILLIS, List.copyOf(domains));
            cache = refreshed;
            return refreshed.domains();
        }
    }

    public void invalidate() {
        cache = new DomainCache(0, List.of());
    }

    public boolean isOfficialUrl(String url) {
        try {
            URI uri = URI.create(url);
            return isOfficialHost(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isOfficialHost(String host) {
        String normalizedHost = normalizeDomain(host);
        if (!StringUtils.hasText(normalizedHost)) {
            return false;
        }
        return officialDomains().stream().anyMatch(domain -> matchesHost(normalizedHost, domain));
    }

    public boolean matchesHost(String host, String domain) {
        String normalizedHost = normalizeDomain(host);
        String normalizedDomain = normalizeDomain(domain);
        return StringUtils.hasText(normalizedHost)
                && StringUtils.hasText(normalizedDomain)
                && (normalizedHost.equals(normalizedDomain) || normalizedHost.endsWith("." + normalizedDomain));
    }

    private String normalizeDomain(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("(?i)^https?://.*")) {
            try {
                normalized = URI.create(normalized).getHost();
            } catch (Exception e) {
                return "";
            }
        }
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        normalized = normalized.replaceFirst("^\\.+", "").replaceFirst("\\.+$", "");
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        try {
            return IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES);
        } catch (Exception e) {
            return "";
        }
    }

    private record DomainCache(long expiresAt, List<String> domains) {
    }
}
