package com.hzmwmt.geo.service;

import com.hzmwmt.common.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Validates website-monitor targets before any connection is made. All resolved
 * addresses must be globally routable; callers must connect to one of the
 * returned addresses rather than resolving the hostname again.
 */
@Service
public class GeoWebsiteUrlPolicy {

    private static final int MAX_URL_LENGTH = 2000;

    public ValidatedUrl validate(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH) {
            throw invalidUrl();
        }
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }
        try {
            URI parsed = URI.create(value);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || parsed.getRawUserInfo() != null
                    || parsed.getRawFragment() != null) {
                throw invalidUrl();
            }
            String parsedHost = parsed.getHost();
            if (!StringUtils.hasText(parsedHost)) {
                throw invalidUrl();
            }
            String host = IDN.toASCII(parsedHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (!host.contains(".") || host.endsWith(".local") || "localhost".equals(host) || isIpLiteral(host)) {
                throw invalidUrl();
            }
            int defaultPort = "https".equals(scheme) ? 443 : 80;
            int port = parsed.getPort() < 0 ? defaultPort : parsed.getPort();
            if (port != defaultPort) {
                throw new AppException("GEO_WEBSITE_PORT_REJECTED", "官网监测仅支持标准 HTTP/HTTPS 端口");
            }
            String path = StringUtils.hasText(parsed.getRawPath()) ? parsed.getRawPath() : "/";
            URI normalized = new URI(scheme, null, host, -1, path, parsed.getRawQuery(), null).normalize();
            InetAddress[] resolved = resolveHost(host);
            if (resolved.length == 0 || Arrays.stream(resolved).anyMatch(this::isForbiddenAddress)) {
                throw new AppException("GEO_WEBSITE_PRIVATE_ADDRESS", "官网地址不能指向内网或保留网络");
            }
            String domain = host.startsWith("www.") ? host.substring(4) : host;
            return new ValidatedUrl(normalized, host, domain, port, List.of(resolved));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw invalidUrl();
        }
    }

    /**
     * Kept overridable so the SSRF policy can be tested against deterministic
     * DNS answers without making a network request.
     */
    protected InetAddress[] resolveHost(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    public boolean sameSite(ValidatedUrl origin, ValidatedUrl redirect) {
        if (origin == null || redirect == null) {
            return false;
        }
        String originDomain = origin.domain();
        String redirectDomain = redirect.domain();
        return originDomain.equals(redirectDomain)
                || redirect.host().endsWith("." + originDomain)
                || origin.host().endsWith("." + redirectDomain);
    }

    private boolean isIpLiteral(String host) {
        return host.contains(":") || host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}");
    }

    boolean isForbiddenAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            int c = bytes[2] & 0xff;
            return a == 0
                    || a == 10
                    || a == 127
                    || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 0 && c == 0)
                    || (a == 192 && b == 0 && c == 2)
                    || (a == 192 && b == 168)
                    || (a == 198 && (b == 18 || b == 19))
                    || (a == 198 && b == 51 && c == 100)
                    || (a == 203 && b == 0 && c == 113)
                    || a >= 224;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 0xff;
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20
                    && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8;
            return uniqueLocal || documentation;
        }
        return true;
    }

    private AppException invalidUrl() {
        return new AppException("PARAM_INVALID", "请输入可公开访问的 HTTP/HTTPS 官网地址");
    }

    public record ValidatedUrl(URI uri, String host, String domain, int port, List<InetAddress> addresses) {
        public String normalizedUrl() {
            return uri.toASCIIString();
        }
    }
}
