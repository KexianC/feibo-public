package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.domain.digital.adapter.port.IDigitalPort;
import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.*;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DigitalWorkMembershipTest {
    private DigitalWorkService service;
    private IDigitalRepository repository;
    private IDigitalPort provider;
    private IEntitlementService quotas;
    @BeforeEach void setup() {
        service = new DigitalWorkService(); repository = mock(IDigitalRepository.class);
        provider = mock(IDigitalPort.class); quotas = mock(IEntitlementService.class);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "port", provider);
        ReflectionTestUtils.setField(service, "entitlementService", quotas);
        ReflectionTestUtils.setField(service, "transactionManager", mock(org.springframework.transaction.PlatformTransactionManager.class));
    }
    private VideoCompositionEntity request() {
        return VideoCompositionEntity.builder().userId(42L).videoTime(1).requestId("stable-request-key")
                .body("{\"items\":[{\"detailList\":[{\"contentType\":\"audio\",\"content\":\"https://owned.example/a.wav\"}]}]}").build();
    }
    @Test void quotaExhaustionNeverSubmitsOrWritesAWork() {
        when(repository.queryOwnedAudioDuration(42L, "https://owned.example/a.wav")).thenReturn(301);
        doThrow(new AppException("QUOTA_EXCEEDED", "额度不足")).when(quotas).reserve(eq(42L), eq("DIGITAL_HUMAN_SECONDS"), eq(301L), anyString());
        assertThatThrownBy(() -> service.createVideoComposition(request())).isInstanceOf(AppException.class);
        verifyNoInteractions(provider);
        verify(repository, never()).saveDigitalWork(any());
        verify(repository, never()).deductUserAccountQuota(any());
    }
    @Test void anUnknownPreviousSubmissionCannotBeSentAgainWithTheSameRequestToken() {
        when(repository.queryOwnedAudioDuration(anyLong(), anyString())).thenReturn(30);
        when(repository.findDigitalWorkByQuotaKey(anyLong(), anyString())).thenReturn(
                DigitalWorkEntity.builder().externalId("pending_abc").build());
        assertThatThrownBy(() -> service.createVideoComposition(request())).isInstanceOf(AppException.class);
        verifyNoInteractions(provider, quotas);
    }
    @Test void durableHoldAndPlaceholderPrecedeProviderSubmissionAndExplicitRejectionRefunds() {
        when(repository.queryOwnedAudioDuration(anyLong(), anyString())).thenReturn(30);
        when(provider.createVideoComposition(anyString())).thenThrow(new VideoCompositionRejectedException());
        assertThatThrownBy(() -> service.createVideoComposition(request())).isInstanceOf(VideoCompositionRejectedException.class);
        var order = inOrder(quotas, repository, provider);
        order.verify(repository).queryOwnedAudioDuration(42L, "https://owned.example/a.wav");
        order.verify(repository).findDigitalWorkByQuotaKey(eq(42L), anyString());
        order.verify(quotas).reserve(eq(42L), eq("DIGITAL_HUMAN_SECONDS"), eq(30L), anyString());
        order.verify(repository).saveDigitalWork(any());
        order.verify(provider).createVideoComposition(anyString());
        order.verify(quotas).release(eq(42L), anyString());
    }
    @Test void transportUncertaintyKeepsItsHoldAndDoesNotRetryProvider() {
        when(repository.queryOwnedAudioDuration(anyLong(), anyString())).thenReturn(30);
        when(provider.createVideoComposition(anyString())).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service.createVideoComposition(request())).isInstanceOf(IllegalStateException.class);
        verify(quotas, never()).release(anyLong(), anyString());
        verify(provider).createVideoComposition(anyString());
    }
}
