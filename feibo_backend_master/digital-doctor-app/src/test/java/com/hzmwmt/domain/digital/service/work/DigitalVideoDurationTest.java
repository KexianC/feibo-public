package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DigitalVideoDurationTest {
    @Test void sumsAllScenesAndUsesServerTextAndSpeedRatherThanClientQuotaHint() {
        String payload = """
                {"videoTime":1,"items":[
                  {"detailList":[{"contentType":"text","content":"一二三四五六七八九十","magicSpeakerId":"real-speaker","speed":0.5}]},
                  {"detailList":[{"contentType":"text","content":"一二三四五六七八九十","magicSpeakerId":"real-speaker","speed":1}]}
                ]}
                """;
        assertThat(DigitalVideoDuration.estimateSeconds(payload, url -> null)).isEqualTo(19);
    }

    @Test void audioUsesOwnedStoredDurationAndRejectsUnregisteredUrls() {
        String payload = """
                {"items":[{"detailList":[{"contentType":"audio","content":"https://owned.example/long.wav","duration":1}]}]}
                """;
        assertThat(DigitalVideoDuration.estimateSeconds(payload, url -> 301)).isEqualTo(301);
        assertThatThrownBy(() -> DigitalVideoDuration.estimateSeconds(payload, url -> null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> DigitalVideoDuration.estimateSeconds(payload, url -> -1)).isInstanceOf(AppException.class);
    }

    @Test void authenticatedActualDurationRoundsUpFractionalSecondsAndRejectsMissingDuration() {
        assertThat(DigitalVideoDuration.normalize(60001L, "milliseconds")).isEqualTo(61);
        assertThat(DigitalVideoDuration.normalize(59L, "seconds")).isEqualTo(59);
        assertThatThrownBy(() -> DigitalVideoDuration.normalize(null, "milliseconds")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> DigitalVideoDuration.normalize(0L, "seconds")).isInstanceOf(AppException.class);
    }

    @Test void secretFieldsCannotBeWrittenIntoThePreSubmissionPlaceholder() {
        String payload = """
                {"items":[{"detailList":[{"contentType":"audio","content":"https://owned.example/a.wav","api_key":"secret"}]}]}
                """;
        assertThatThrownBy(() -> DigitalVideoDuration.estimateSeconds(payload, url -> 10)).isInstanceOf(AppException.class);
    }
}
