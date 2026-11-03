package com.hzmwmt.types.util;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FileUploadDurationTest {
    @Test
    void normalWavKeepsSecondsAndRoundingWhilePositiveSubsecondAudioIsUsable() {
        assertThat(FileUploadUtil.getAudioDurationWithTika(wav(1.6))).isEqualTo(2);
        assertThat(FileUploadUtil.getAudioDurationWithTika(wav(0.2))).isEqualTo(1);
    }

    @Test
    void validBatchKeepsFileOrder() {
        assertThat(FileUploadUtil.getAudioDurationsWithTika(List.of(wav(1.6), wav(0.2))))
                .containsExactly(2, 1);
    }

    @Test
    void truncatedPcmTrackIsRejectedInsteadOfTrustingTheDeclaredWavLength() throws Exception {
        byte[] complete = wav(1.6).getBytes();
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationWithTika(new MockMultipartFile(
                "file", "truncated.wav", "audio/wav", Arrays.copyOf(complete, complete.length - 32))))
                .isInstanceOf(AppException.class);
    }

    @Test
    void invalidFileRejectsWholeBatchInsteadOfReturningNegativePlaceholder() {
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationsWithTika(
                List.of(wav(1.6), fakeMp3(), wav(0.2))))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("无法识别有效媒体时长");
    }

    @Test
    void extensionAndMimeTypeDoNotTurnTextOrId3TagsIntoAnMp3AudioTrack() {
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationWithTika(fakeMp3()))
                .isInstanceOf(AppException.class);
        byte[] id3Only = {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 0};
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationWithTika(
                new MockMultipartFile("file", "tags.mp3", "audio/mpeg", id3Only)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void emptyInputsFailWithAnExplicitApplicationError() {
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationWithTika(null))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode()));
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationWithTika(
                new MockMultipartFile("file", "empty.mp3", "audio/mpeg", new byte[0])))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> FileUploadUtil.getAudioDurationsWithTika(List.of()))
                .isInstanceOf(AppException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "0", "-1", "86400.01", "unavailable"})
    void unusableMetadataNeverBecomesAQuotaDuration(String value) {
        Metadata metadata = new Metadata();
        metadata.set("xmpDM:duration", value);
        assertThat(FileUploadUtil.readDurationSeconds(metadata)).isNull();
    }

    @Test
    void supportedMetadataKeysStillRespectSecondsBoundsAndRounding() {
        Metadata metadata = new Metadata();
        metadata.set("xmpDM:duration", "NaN");
        metadata.set("duration", "121.96800231933594");
        assertThat(FileUploadUtil.readDurationSeconds(metadata)).isEqualTo(122);
        metadata.set("duration", "86400");
        assertThat(FileUploadUtil.readDurationSeconds(metadata)).isEqualTo(86400);
        metadata.set("duration", "0.2");
        assertThat(FileUploadUtil.readDurationSeconds(metadata)).isEqualTo(1);
    }

    private static MockMultipartFile fakeMp3() {
        return new MockMultipartFile("file", "fake.mp3", "audio/mpeg",
                "This is not an audio stream".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile wav(double seconds) {
        int sampleRate = 8000;
        int dataLength = (int) Math.round(seconds * sampleRate) * 2;
        ByteBuffer bytes = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLength);
        bytes.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        bytes.putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2);
        bytes.putShort((short) 2).putShort((short) 16);
        bytes.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLength);
        return new MockMultipartFile("file", "sample.wav", "audio/wav", bytes.array());
    }
}
