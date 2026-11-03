package com.hzmwmt.infrastructure.gateway.digital;

import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class SafeVideoDownloader {
    private static final int MAX_URL_LENGTH = 2048;

    private final DigitalHumanApiProperties properties;
    private final OkHttpClient httpClient;

    public SafeVideoDownloader(DigitalHumanApiProperties properties) {
        this.properties = properties;
        Dns safeDns = hostname -> resolvePublicAddresses(hostname);
        this.httpClient = new OkHttpClient.Builder()
                .dns(safeDns)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMinutes(2))
                .callTimeout(Duration.ofMinutes(3))
                .build();
    }

    public URI validateResultUri(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
            throw invalidResultUrl();
        }
        try {
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !isAllowedHost(uri.getHost())) {
                throw invalidResultUrl();
            }
            // Resolve before any request so private and special-purpose targets
            // fail without opening a socket. OkHttp repeats the same check in Dns.
            resolvePublicAddresses(uri.getHost());
            return uri;
        } catch (URISyntaxException | UnknownHostException e) {
            throw invalidResultUrl();
        }
    }

    public DownloadedVideo download(String value) throws IOException {
        URI uri = validateResultUri(value);
        Request request = new Request.Builder().url(uri.toString()).get().build();
        Path tempFile = null;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.isRedirect()) {
                throw new IOException("Provider video download returned an invalid HTTP status");
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Provider video response is empty");
            }
            long maxBytes = validatedMaxBytes();
            long declaredLength = body.contentLength();
            if (declaredLength > maxBytes) {
                throw new IOException("Provider video exceeds the configured size limit");
            }
            String contentType = body.contentType() == null
                    ? "application/octet-stream"
                    : body.contentType().toString();
            if (!isAllowedContentType(contentType)) {
                throw new IOException("Provider response is not a video");
            }

            tempFile = Files.createTempFile("feibo-provider-video-", ".bin");
            long total = 0;
            try (InputStream input = body.byteStream();
                 var output = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("Provider video exceeds the configured size limit");
                    }
                    output.write(buffer, 0, read);
                }
            }
            String extension = detectVideoExtension(tempFile);
            return new DownloadedVideo(tempFile, contentType, extension, total);
        } catch (Exception e) {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("Provider video download failed", e);
        }
    }

    private List<InetAddress> resolvePublicAddresses(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Arrays.asList(InetAddress.getAllByName(hostname));
        if (addresses.isEmpty() || addresses.stream().anyMatch(this::isUnsafeAddress)) {
            throw new UnknownHostException("Host does not resolve exclusively to public addresses");
        }
        return addresses;
    }

    private boolean isUnsafeAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        }
        if (bytes.length != 4) {
            return true;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127);
    }

    private boolean isAllowedHost(String host) {
        List<String> allowedHosts = properties.getResultAllowedHosts();
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        String normalizedHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        return allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> IDN.toASCII(value.trim()).toLowerCase(Locale.ROOT))
                .anyMatch(normalizedHost::equals);
    }

    private long validatedMaxBytes() {
        long maxBytes = properties.getMaxResultBytes();
        if (maxBytes < 1024 || maxBytes > 2L * 1024 * 1024 * 1024) {
            throw new IllegalStateException("Digital-human result size limit is invalid");
        }
        return maxBytes;
    }

    private boolean isAllowedContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("video/")
                || normalized.startsWith("application/mp4")
                || normalized.startsWith("application/octet-stream");
    }

    private String detectVideoExtension(Path path) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(16);
        }
        if (header.length >= 12
                && header[4] == 'f' && header[5] == 't'
                && header[6] == 'y' && header[7] == 'p') {
            return ".mp4";
        }
        if (header.length >= 4
                && (header[0] & 0xff) == 0x1a && (header[1] & 0xff) == 0x45
                && (header[2] & 0xff) == 0xdf && (header[3] & 0xff) == 0xa3) {
            return ".webm";
        }
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'A' && header[9] == 'V' && header[10] == 'I') {
            return ".avi";
        }
        throw new IOException("Provider response does not contain a supported video signature");
    }

    private AppException invalidResultUrl() {
        return new AppException(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "Digital-human result URL is not allowed");
    }

    public record DownloadedVideo(
            Path path,
            String contentType,
            String extension,
            long size) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
