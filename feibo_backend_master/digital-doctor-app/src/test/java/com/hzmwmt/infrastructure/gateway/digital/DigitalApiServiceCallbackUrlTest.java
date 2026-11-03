package com.hzmwmt.infrastructure.gateway.digital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionSubmissionEntity;
import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalApiServiceCallbackUrlTest {

    @Test
    void callbackUrlCarriesOnlyTheIssuedTaskCredentialAndPreservesOtherParameters() {
        DigitalHumanApiProperties properties = new DigitalHumanApiProperties();
        properties.setCallbackUrl(
                "https://callback.example/api/v1/digital-human/work/video-callback"
                        + "?tenant=primary&callbackToken=obsolete-global-token");
        DigitalApiService service = new DigitalApiService();
        ReflectionTestUtils.setField(service, "apiProperties", properties);
        DigitalHumanCallbackVerifier.IssuedCredential credential =
                new DigitalHumanCallbackVerifier.IssuedCredential(
                        "I".repeat(22), "T".repeat(43));

        UriComponents uri = UriComponentsBuilder.fromUriString(
                service.authenticatedCallbackUrl(credential)).build();

        assertEquals("primary", uri.getQueryParams().getFirst("tenant"));
        assertEquals(credential.id(), uri.getQueryParams().getFirst("callbackId"));
        assertEquals(credential.token(), uri.getQueryParams().getFirst("callbackToken"));
    }

    @Test
    void callbackUrlStillRequiresHttps() {
        DigitalHumanApiProperties properties = new DigitalHumanApiProperties();
        properties.setCallbackUrl("http://callback.example/work/video-callback");
        DigitalApiService service = new DigitalApiService();
        ReflectionTestUtils.setField(service, "apiProperties", properties);

        assertThrows(
                AppException.class,
                () -> service.authenticatedCallbackUrl(
                        new DigitalHumanCallbackVerifier.IssuedCredential(
                                "I".repeat(22), "T".repeat(43))));
    }

    @Test
    void successfulProviderSubmissionBindsIssuedCredentialToReturnedTask() {
        DigitalHumanApiProperties properties = new DigitalHumanApiProperties();
        properties.setCallbackUrl("https://callback.example/work/video-callback");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        DigitalHumanCallbackVerifier callbackVerifier =
                mock(DigitalHumanCallbackVerifier.class);
        DigitalHumanCallbackVerifier.IssuedCredential credential =
                new DigitalHumanCallbackVerifier.IssuedCredential(
                        "I".repeat(22), "T".repeat(43));
        when(callbackVerifier.issue()).thenReturn(credential);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"code\":200,\"data\":\"task_123\"}")
                                .build()))
                .build();
        DigitalApiService service = new DigitalApiService();
        ReflectionTestUtils.setField(service, "apiProperties", properties);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "webClient", webClient);
        ReflectionTestUtils.setField(service, "callbackVerifier", callbackVerifier);

        VideoCompositionSubmissionEntity submission =
                service.createVideoComposition("{\"text\":\"hello\"}");

        assertEquals("task_123", submission.getExternalId());
        verify(callbackVerifier).bind(credential, "task_123");
    }
}
