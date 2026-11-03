package com.hzmwmt.types.util;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

/**
 * Explicit regression acceptance using external private fixtures, never checked into source.
 * Run with -Dtest=FileUploadDurationTest,FileUploadDurationFixtureIT and both
 * -Daudio.audit.regressionFile=... and -Daudio.audit.controlFile=....
 * Required fixtures fail acceptance when missing; these tests never silently skip.
 */
class FileUploadDurationFixtureIT {
    @Test
    void recoversTheOriginalMp3ThatTikaMistakesForAacWithoutTrustingItsExtension() throws Exception {
        Path path = fixture("audio.audit.regressionFile");
        byte[] bytes = Files.readAllBytes(path);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo("530f90539a0d68ad409cd3dd640d7688d7285841b990e0747822878c1bcf1494");
        Metadata originalDetection = new Metadata();
        try (InputStream stream = Files.newInputStream(path)) {
            new AutoDetectParser().parse(stream, new DefaultHandler(), originalDetection, new ParseContext());
        }
        assertThat(originalDetection.get(Metadata.CONTENT_TYPE)).isEqualTo("audio/x-aac");
        assertThat(originalDetection.get("xmpDM:duration")).isNull();
        assertThat(FileUploadUtil.getAudioDurationWithTika(
                new MockMultipartFile("file", "recording.bin", "application/octet-stream", bytes)))
                .isEqualTo(119);
    }

    @Test
    void normalMp3KeepsItsPreviousRoundedDuration() throws Exception {
        Path path = fixture("audio.audit.controlFile");
        assertThat(FileUploadUtil.getAudioDurationWithTika(
                new MockMultipartFile("file", "control.mp3", "audio/mpeg", Files.readAllBytes(path))))
                .isEqualTo(122);
    }

    private static Path fixture(String property) {
        String value = System.getProperty(property);
        assertThat(value).as("Required external fixture: -D%s=/absolute/path", property).isNotBlank();
        Path path = Path.of(value);
        assertThat(path).isRegularFile();
        return path;
    }
}
