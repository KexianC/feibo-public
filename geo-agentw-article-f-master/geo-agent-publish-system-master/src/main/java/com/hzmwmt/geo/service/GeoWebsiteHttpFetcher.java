package com.hzmwmt.geo.service;

import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Small pinned-address HTTP client used only by website audits. It validates
 * DNS once and connects directly to the approved address, while retaining the
 * original hostname for the Host header, TLS SNI, and certificate verification.
 */
@Service
public class GeoWebsiteHttpFetcher {

    private static final int MAX_HEADER_BYTES = 65536;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    @Resource
    private GeoWebsiteUrlPolicy urlPolicy;

    @Resource
    private GeoProperties geoProperties;

    public FetchResult fetchHtml(String rawUrl) {
        return fetch(rawUrl, ResourceKind.HTML);
    }

    public FetchResult fetchText(String rawUrl) {
        return fetch(rawUrl, ResourceKind.TEXT);
    }

    private FetchResult fetch(String rawUrl, ResourceKind resourceKind) {
        long started = System.nanoTime();
        GeoWebsiteUrlPolicy.ValidatedUrl origin = urlPolicy.validate(rawUrl);
        GeoWebsiteUrlPolicy.ValidatedUrl current = origin;
        String requestedUrl = origin.normalizedUrl();
        int maxRedirects = Math.max(0, Math.min(5, geoProperties.getWebsiteMaxRedirects()));
        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            RawResponse response = request(current);
            if (REDIRECT_STATUSES.contains(response.status()) && StringUtils.hasText(response.header("location"))) {
                if (redirectCount >= maxRedirects) {
                    throw new AppException("GEO_WEBSITE_REDIRECT_LIMIT", "官网重定向次数过多");
                }
                URI redirectUri = current.uri().resolve(response.header("location").trim());
                GeoWebsiteUrlPolicy.ValidatedUrl redirect = urlPolicy.validate(redirectUri.toString());
                if (!urlPolicy.sameSite(origin, redirect)) {
                    throw new AppException("GEO_WEBSITE_CROSS_SITE_REDIRECT", "官网重定向到了其他域名，已停止检测");
                }
                current = redirect;
                continue;
            }
            String contentType = response.header("content-type");
            if (response.status() >= 200 && response.status() < 300
                    && !resourceKind.accepts(contentType)) {
                throw new AppException("GEO_WEBSITE_CONTENT_TYPE", "官网返回的内容不是可检测的网页文本");
            }
            byte[] decoded = decodeBody(response.body(), response.header("content-encoding"));
            return new FetchResult(
                    requestedUrl,
                    current.normalizedUrl(),
                    response.status(),
                    contentType,
                    response.headers(),
                    decoded,
                    elapsedMillis(started));
        }
        throw new AppException("GEO_WEBSITE_FETCH_FAILED", "官网检测请求失败");
    }

    private RawResponse request(GeoWebsiteUrlPolicy.ValidatedUrl target) {
        for (InetAddress address : target.addresses()) {
            try {
                return requestAddress(target, address);
            } catch (IOException e) {
                // Try the next address returned by the validated DNS lookup.
            }
        }
        throw new AppException("GEO_WEBSITE_UNREACHABLE", "官网暂时无法访问");
    }

    private RawResponse requestAddress(GeoWebsiteUrlPolicy.ValidatedUrl target, InetAddress address) throws IOException {
        Socket transport = new Socket();
        try {
            transport.connect(
                    new InetSocketAddress(address, target.port()),
                    Math.max(500, geoProperties.getWebsiteConnectTimeoutMs()));
            transport.setSoTimeout(Math.max(1000, geoProperties.getWebsiteReadTimeoutMs()));

            Socket socket = transport;
            if ("https".equalsIgnoreCase(target.uri().getScheme())) {
                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(transport, target.host(), target.port(), true);
                SSLParameters sslParameters = sslSocket.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslParameters.setServerNames(List.of(new SNIHostName(target.host())));
                sslSocket.setSSLParameters(sslParameters);
                sslSocket.startHandshake();
                socket = sslSocket;
            }

            try (Socket closeable = socket;
                 OutputStream output = closeable.getOutputStream();
                 BufferedInputStream input = new BufferedInputStream(closeable.getInputStream())) {
                String requestTarget = StringUtils.hasText(target.uri().getRawPath()) ? target.uri().getRawPath() : "/";
                if (StringUtils.hasText(target.uri().getRawQuery())) {
                    requestTarget += "?" + target.uri().getRawQuery();
                }
                String request = "GET " + requestTarget + " HTTP/1.1\r\n"
                        + "Host: " + target.host() + "\r\n"
                        + "User-Agent: Feibo-GEO-Website-Monitor/1.0\r\n"
                        + "Accept: text/html,application/xhtml+xml,text/plain,application/xml;q=0.8\r\n"
                        + "Accept-Encoding: gzip, identity\r\n"
                        + "Connection: close\r\n\r\n";
                output.write(request.getBytes(StandardCharsets.US_ASCII));
                output.flush();
                return readResponse(input);
            }
        } catch (IOException | RuntimeException e) {
            try {
                transport.close();
            } catch (IOException ignored) {
                // Preserve the original failure.
            }
            throw e;
        }
    }

    private RawResponse readResponse(BufferedInputStream input) throws IOException {
        String statusLine = readLine(input, 8192);
        if (!StringUtils.hasText(statusLine)) {
            throw new IOException("empty status line");
        }
        String[] statusParts = statusLine.split("\\s+", 3);
        if (statusParts.length < 2) {
            throw new IOException("invalid status line");
        }
        int status;
        try {
            status = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("invalid response status", e);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        int headerBytes = statusLine.length();
        while (true) {
            String line = readLine(input, 8192);
            headerBytes += line.length();
            if (headerBytes > MAX_HEADER_BYTES) {
                throw new IOException("response headers too large");
            }
            if (line.isEmpty()) {
                break;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            headers.merge(name, value, (left, right) -> left + ", " + right);
        }

        int maxBytes = Math.max(65536, geoProperties.getWebsiteMaxBytes());
        byte[] body;
        String transferEncoding = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
        if (transferEncoding.contains("chunked")) {
            body = readChunked(input, maxBytes);
        } else {
            long contentLength = parseContentLength(headers.get("content-length"));
            if (contentLength > maxBytes) {
                throw new AppException("GEO_WEBSITE_RESPONSE_TOO_LARGE", "官网页面内容超过检测上限");
            }
            body = readLimited(input, maxBytes, contentLength);
        }
        return new RawResponse(status, headers, body);
    }

    private byte[] decodeBody(byte[] body, String contentEncoding) {
        if (!StringUtils.hasText(contentEncoding) || "identity".equalsIgnoreCase(contentEncoding.trim())) {
            return body;
        }
        if (!contentEncoding.toLowerCase(Locale.ROOT).contains("gzip")) {
            throw new AppException("GEO_WEBSITE_ENCODING_UNSUPPORTED", "官网使用了暂不支持的内容编码");
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return readLimited(gzip, Math.max(65536, geoProperties.getWebsiteMaxBytes()), -1);
        } catch (IOException e) {
            throw new AppException("GEO_WEBSITE_ENCODING_INVALID", "官网返回内容解压失败");
        }
    }

    private byte[] readChunked(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(input, 128);
            int extension = sizeLine.indexOf(';');
            String sizeToken = (extension >= 0 ? sizeLine.substring(0, extension) : sizeLine).trim();
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeToken, 16);
            } catch (NumberFormatException e) {
                throw new IOException("invalid chunk size", e);
            }
            if (chunkSize == 0) {
                while (!readLine(input, 8192).isEmpty()) {
                    // Consume optional trailers.
                }
                break;
            }
            if (chunkSize < 0 || chunkSize > maxBytes - output.size()) {
                throw new AppException("GEO_WEBSITE_RESPONSE_TOO_LARGE", "官网页面内容超过检测上限");
            }
            copyExact(input, output, (int) chunkSize);
            if (!readLine(input, 2).isEmpty()) {
                throw new IOException("invalid chunk terminator");
            }
        }
        return output.toByteArray();
    }

    private byte[] readLimited(InputStream input, int maxBytes, long expectedLength) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long remaining = expectedLength;
        while (expectedLength < 0 || remaining > 0) {
            int requested = expectedLength < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
            int read = input.read(buffer, 0, requested);
            if (read < 0) {
                break;
            }
            if (output.size() + read > maxBytes) {
                throw new AppException("GEO_WEBSITE_RESPONSE_TOO_LARGE", "官网页面内容超过检测上限");
            }
            output.write(buffer, 0, read);
            if (expectedLength >= 0) {
                remaining -= read;
            }
        }
        if (expectedLength >= 0 && remaining > 0) {
            throw new IOException("truncated response body");
        }
        return output.toByteArray();
    }

    private void copyExact(InputStream input, OutputStream output, int length) throws IOException {
        byte[] buffer = new byte[8192];
        int remaining = length;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("truncated chunk");
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private String readLine(InputStream input, int maxLength) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (line.size() <= maxLength) {
            int current = input.read();
            if (current < 0) {
                if (line.size() == 0) {
                    return "";
                }
                break;
            }
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(current);
            previous = current;
        }
        if (line.size() > maxLength) {
            throw new IOException("response line too long");
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }

    private long parseContentLength(String value) {
        if (!StringUtils.hasText(value)) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private enum ResourceKind {
        HTML {
            @Override
            boolean accepts(String contentType) {
                String normalized = normalize(contentType);
                return normalized.startsWith("text/html") || normalized.startsWith("application/xhtml+xml");
            }
        },
        TEXT {
            @Override
            boolean accepts(String contentType) {
                String normalized = normalize(contentType);
                return normalized.startsWith("text/")
                        || normalized.startsWith("application/xml")
                        || normalized.startsWith("application/xhtml+xml");
            }
        };

        abstract boolean accepts(String contentType);

        static String normalize(String contentType) {
            return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        }
    }

    private record RawResponse(int status, Map<String, String> headers, byte[] body) {
        String header(String name) {
            return headers.getOrDefault(name.toLowerCase(Locale.ROOT), "");
        }
    }

    public record FetchResult(
            String requestedUrl,
            String finalUrl,
            int status,
            String contentType,
            Map<String, String> headers,
            byte[] body,
            long latencyMs) {
    }
}
