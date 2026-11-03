package com.hzmwmt.infrastructure.gateway.digital;

import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeVideoDownloaderTest {

    @Test
    void nonHttpsAndNonAllowlistedUrlsAreRejected() {
        DigitalHumanApiProperties properties = new DigitalHumanApiProperties();
        properties.setResultAllowedHosts(List.of("cdn.provider.example"));
        SafeVideoDownloader downloader = new SafeVideoDownloader(properties);

        assertThrows(AppException.class, () -> downloader.validateResultUri("file:///etc/passwd"));
        assertThrows(AppException.class, () -> downloader.validateResultUri("http://cdn.provider.example/a.mp4"));
        assertThrows(AppException.class, () -> downloader.validateResultUri("https://evil.example/a.mp4"));
    }

    @Test
    void loopbackIsRejectedEvenWhenMistakenlyAllowlisted() {
        DigitalHumanApiProperties properties = new DigitalHumanApiProperties();
        properties.setResultAllowedHosts(List.of("127.0.0.1", "localhost"));
        SafeVideoDownloader downloader = new SafeVideoDownloader(properties);

        assertThrows(AppException.class, () -> downloader.validateResultUri("https://127.0.0.1/a.mp4"));
        assertThrows(AppException.class, () -> downloader.validateResultUri("https://localhost/a.mp4"));
    }
}
