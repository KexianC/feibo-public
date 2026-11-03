package com.hzmwmt.videogeneration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class VideoProviderGatewayTest {
    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(body); return response;
    }
    @Test void seedancePayloadUsesContentArrayAndLowercaseResolution() {
        var payload = VideoProviderGateway.payload("seedance", "model-id", new VideoGenerationService.Create("test", "seedance", "a forest", 5, "720P", "9:16"));
        assertThat(payload).containsEntry("resolution", "720p").containsEntry("ratio", "9:16").containsKey("content");
    }
    @Test void minimaxPayloadDoesNotSendUnsupportedRatio() {
        var payload = VideoProviderGateway.payload("minimax", "MiniMax-Hailuo-2.3", new VideoGenerationService.Create("test", "minimax", "a forest", 6, "1080P", "16:9"));
        assertThat(payload).containsEntry("resolution", "1080P").containsEntry("duration", 6).doesNotContainKey("ratio");
    }
    @Test void seedanceSuccessWithNullErrorIsAccepted() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> response("{\"status\":\"succeeded\",\"error\":null,\"content\":{\"video_url\":\"https://example.com/video.mp4\"}}"));
        var progress = new VideoProviderGateway(client, new ObjectMapper()).query("seedance", "test-key", "task-id");
        assertThat(progress.status()).isEqualTo("succeeded");
    }
    @Test void seedanceFailedErrorMapsToTerminalFailure() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> response("{\"status\":\"failed\",\"error\":{\"message\":\"private upstream payload\"}}"));
        var progress = new VideoProviderGateway(client, new ObjectMapper()).query("seedance", "test-key", "task-id");
        assertThat(progress.status()).isEqualTo("failed"); assertThat(progress.error()).doesNotContain("private");
    }
    @Test void minimaxResolvesFileAfterSuccess() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var taskResponse = response("{\"status\":\"Success\",\"file_id\":\"123\",\"base_resp\":{\"status_code\":0}}");
        var fileResponse = response("{\"file\":{\"download_url\":\"https://example.com/video.mp4\"}}");
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(taskResponse, fileResponse);
        var progress = new VideoProviderGateway(client, new ObjectMapper()).query("minimax", "test-key", "123");
        assertThat(progress.url()).isEqualTo("https://example.com/video.mp4");
        verify(client, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
    }
    @Test void providerBusinessErrorIsNotLeaked() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> response("{\"base_resp\":{\"status_code\":1008,\"status_msg\":\"private value\"}}"));
        assertThatThrownBy(() -> new VideoProviderGateway(client, new ObjectMapper()).query("minimax", "test-key", "123"))
                .hasMessageContaining("供应商拒绝").hasMessageNotContaining("private");
    }
}
